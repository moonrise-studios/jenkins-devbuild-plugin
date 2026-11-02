package gg.moonrise.jenkins.devbuild.publish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gg.moonrise.jenkins.devbuild.settings.ArtifactSettings;
import gg.moonrise.jenkins.devbuild.settings.NotifyConditions;
import gg.moonrise.jenkins.devbuild.settings.OversizePolicy;
import gg.moonrise.jenkins.devbuild.util.TextUtils;
import hudson.AbortException;
import hudson.model.Result;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PublishLogicTest {

    private static final long MIB = 1024L * 1024L;
    private final PrintStream log = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

    private static BuildFile file(String name, long size) {
        return new BuildFile(name, "libs/" + name, size, "https://ci/job/x/1/artifact/libs/" + name,
                () -> new ByteArrayInputStream(new byte[0]));
    }

    private static List<String> names(List<BuildFile> files) {
        return files.stream().map(BuildFile::name).toList();
    }

    @Test
    void moreThanTenFilesAreSplitIntoMessagesOfTen() throws Exception {
        ArtifactSettings settings = new ArtifactSettings();
        settings.setMaxFiles(25);
        List<BuildFile> files = IntStream.range(0, 23).mapToObj(i -> file("f" + i + ".jar", 10)).toList();

        UploadPlan plan = UploadPlanner.plan(files, settings, log);

        assertEquals(List.of(10, 10, 3), plan.batches().stream().map(List::size).toList());
        assertEquals(23, plan.attached().size());
        assertTrue(plan.linked().isEmpty());
    }

    @Test
    void maxFilesLimitsUploadsAndListsTheRest() throws Exception {
        ArtifactSettings settings = new ArtifactSettings();
        settings.setMaxFiles(2);
        UploadPlan plan = UploadPlanner.plan(List.of(file("a", 1), file("b", 1), file("c", 1)), settings, log);
        assertEquals(List.of("a", "b"), names(plan.attached()));
        assertEquals(List.of("c"), names(plan.linked()));
    }

    @Test
    void budgetIsRespectedPerMessage() throws Exception {
        ArtifactSettings settings = new ArtifactSettings();
        settings.setMaxUploadSizeMb(10);
        List<BuildFile> files = List.of(file("a", 6 * MIB), file("b", 3 * MIB), file("c", 2 * MIB), file("d", 10 * MIB));
        UploadPlan plan = UploadPlanner.plan(files, settings, log);
        assertEquals(List.of(List.of("a", "b"), List.of("c"), List.of("d")),
                plan.batches().stream().map(PublishLogicTest::names).toList());
    }

    @Test
    void oversizePolicies() throws Exception {
        ArtifactSettings settings = new ArtifactSettings();
        settings.setMaxUploadSizeMb(1);
        List<BuildFile> files = List.of(file("small", 10), file("big", 2 * MIB));

        UploadPlan linked = UploadPlanner.plan(files, settings, log);
        assertEquals(List.of("small"), names(linked.attached()));
        assertEquals(List.of("big"), names(linked.linked()));

        settings.setOversizePolicy(OversizePolicy.SKIP);
        UploadPlan skipped = UploadPlanner.plan(files, settings, log);
        assertEquals(List.of("small"), names(skipped.attached()));
        assertTrue(skipped.linked().isEmpty());

        settings.setOversizePolicy(OversizePolicy.FAIL);
        AbortException e = assertThrows(AbortException.class, () -> UploadPlanner.plan(files, settings, log));
        assertTrue(e.getMessage().contains("libs/big (2.0 MiB)"), e.getMessage());
    }

    @Test
    void attachmentDependsOnResult() {
        ArtifactSettings settings = new ArtifactSettings();
        assertTrue(settings.attachesFor(Result.SUCCESS));
        assertEquals(false, settings.attachesFor(Result.UNSTABLE));
        settings.setAttachOnUnstable(true);
        assertTrue(settings.attachesFor(Result.UNSTABLE));
        assertEquals(false, settings.attachesFor(Result.FAILURE));
        settings.setOnlyOnSuccess(false);
        assertTrue(settings.attachesFor(Result.FAILURE));
        settings.setEnabled(false);
        assertEquals(false, settings.attachesFor(Result.SUCCESS));
    }

    @Test
    void notifyDecisions() {
        NotifyConditions defaults = new NotifyConditions();
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(defaults, Result.SUCCESS, null, null));
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(defaults, Result.UNSTABLE, null, null));
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(defaults, Result.FAILURE, null, null));
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(defaults, Result.ABORTED, null, null));
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(defaults, Result.NOT_BUILT, null, null));

        NotifyConditions quiet = new NotifyConditions();
        quiet.setNotifyOnSuccess(false);
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(quiet, Result.SUCCESS, Result.SUCCESS, null));
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(quiet, Result.SUCCESS, null, null));
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(quiet, Result.SUCCESS, Result.FAILURE, null));
        quiet.setNotifyOnBackToNormal(false);
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(quiet, Result.SUCCESS, Result.FAILURE, null));

        NotifyConditions branches = new NotifyConditions();
        branches.setBranchPattern("main|release/.*");
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(branches, Result.SUCCESS, null, "release/2"));
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(branches, Result.SUCCESS, null, "mainline"));
        assertInstanceOf(NotifyDecision.Send.class, NotifyDecision.evaluate(branches, Result.SUCCESS, null, null));
        branches.setBranchPattern("([");
        assertInstanceOf(NotifyDecision.Skip.class, NotifyDecision.evaluate(branches, Result.SUCCESS, null, "main"));
    }

    @Test
    void artifactFieldFitsTheLimit() {
        List<BuildFile> files = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            files.add(file("module-number-" + i + "-1.0.0-SNAPSHOT.jar", 1234567));
        }
        String value = ArtifactListFormatter.fieldValue(files, Set.of(files.get(0)), 1024);
        assertTrue(value.length() <= 1024, "length " + value.length());
        assertTrue(value.startsWith("[module-number-0-1.0.0-SNAPSHOT.jar](https://ci/job/x/1/artifact/libs/"
                + "module-number-0-1.0.0-SNAPSHOT.jar) · 1.2 MiB (not attached)"), value);
        assertTrue(value.endsWith(" more"), value);
        assertEquals("a\\_b\\*c · 5 B", ArtifactListFormatter.fieldValue(
                List.of(new BuildFile("a_b*c", "a_b*c", 5, null, () -> new ByteArrayInputStream(new byte[0]))),
                Set.of(), 1024));
    }

    @Test
    void textHelpers() {
        assertEquals("abc", TextUtils.truncate("abc", 3));
        assertEquals("ab…", TextUtils.truncate("abcd", 3));
        assertEquals("a…", TextUtils.truncate("a😀b", 3));
        assertEquals("**/*.jar,a/*.zip", TextUtils.normalizePatterns(" **/*.jar , ,a/*.zip "));
        assertEquals("1.0 KiB", TextUtils.formatSize(1024));
        assertEquals("999 B", TextUtils.formatSize(999));
    }
}
