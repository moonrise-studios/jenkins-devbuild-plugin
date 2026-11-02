# DevBuild Discord Publisher

DevBuild Discord Publisher is a Jenkins plugin that posts finished builds to a Discord channel through a webhook. Each post contains an embed that describes the build, and the built jar files are uploaded as real Discord attachments inside it, so people can download a dev build straight from Discord.

It works with every job type: Freestyle (and other classic project types such as Maven and Matrix), Pipeline, and Multibranch Pipeline and Organization Folders. Every setting can be configured per job or per folder, with global defaults.

## Features

- An embed per build with a title, link, colour by result, the Moonrise Studios logo as thumbnail, and optional fields for the result, duration, branch, commit, change log and file list. Author, thumbnail, image, footer, timestamp and custom fields are configurable, and all text is templated with build variables.
- Jar files (or any Ant pattern) uploaded as Discord attachments. Archived artifacts are used by default, with a fallback to the workspace.
- One message per build with the files inside the embed: the build card is laid out with Discord components, so the files show as download cards within it. Set `placement` to `AFTER_EMBED` to post a classic embed with the files in a separate message right below it, or to `WITH_EMBED` to attach them to the classic embed message, where Discord shows them above the embed.
- Large uploads are split automatically: at most 10 files per message and a configurable upload budget per message. Remaining files are sent in further follow-up messages to the same channel, thread or forum post. Files that can never fit can be linked to Jenkins, skipped, or fail the step.
- Posts into a channel, an existing thread or forum post (`threadId`), or a new forum post per build (`threadName`, with forum tags).
- Plain message text above the embed with role or user mentions, a custom username and avatar, silent messages, and control over which mentions may ping.
- Notify conditions per result, a back-to-normal notification, and a branch filter for Multibranch jobs.
- Webhook URLs stored as Secret text credentials (folder credentials work) or as encrypted fields. The URL and token never appear in build logs.
- Configuration at four levels: the step or post-build action, the job, every enclosing folder, and the global configuration. Each section is inherited separately.
- Automatic posting from a job or folder property, so Multibranch projects need no Jenkinsfile changes.
- Rate limits are handled: HTTP 429 responses are retried after the delay Discord asks for. Network problems never fail or hang a build.
- Pipeline step with map arguments, Snippet Generator support, and Configuration as Code (JCasC) support.

### Why not the Discord Notifier plugin?

[Discord Notifier](https://plugins.jenkins.io/discord-notifier/) only links artifacts in text and can attach at most one file. It has no thread or forum support, no folder or Multibranch configuration and no credentials support. This plugin was written to upload every build artifact and to be configured per project.

## Supported job types

| Job type | Post-build action | Pipeline step | Job property | Folder property |
| --- | --- | --- | --- | --- |
| Freestyle, Maven, Matrix and other classic projects | Yes | n/a | Yes | Yes (when inside a folder) |
| Pipeline | n/a | Yes | Yes | Yes (when inside a folder) |
| Multibranch Pipeline branches | n/a | Yes | n/a | Yes, on the Multibranch project or any parent folder |
| Organization Folder repositories | n/a | Yes | n/a | Yes, on the organization folder, the repository project or any parent folder |

For Matrix and Maven module builds only the parent build is posted automatically; the post-build action runs wherever Jenkins runs publishers.

## Installation

### Requirements

- Jenkins 2.555.3 or newer.
- Java 21 or newer on the controller (required by the Jenkins 2.555 line).
- These plugins, which are dependencies: Folders (`cloudbees-folder`), Credentials (`credentials`), Plain Credentials (`plain-credentials`) and Structs (`structs`). Pipeline features need the usual Pipeline plugins (`workflow-aggregator` or at least `workflow-job`, `workflow-cps` and `workflow-basic-steps`).

### A) Upload the plugin file

1. Download `devbuild.hpi` from the [GitHub Releases](https://github.com/moonrise-studios/jenkins-devbuild-plugin/releases) page, or use the GitHub CLI:

   ```sh
   gh release download v1.0.0 -R moonrise-studios/jenkins-devbuild-plugin -p devbuild.hpi
   ```

2. In Jenkins, open **Manage Jenkins > Plugins > Advanced settings**, choose the file under **Deploy Plugin** and click **Deploy**.
3. Restart Jenkins when the update page offers it (or restart anyway to be safe).

What happens to the dependencies was verified in the Jenkins 2.555.3 source (`PluginManager.doUploadPlugin` and `UpdateSite.Plugin.deploy`). Jenkins reads the `Plugin-Dependencies` entry from the uploaded file's manifest and installs every required dependency that is missing, older than the required version, or disabled, from the configured update center, before installing the uploaded plugin. A dependency that the update center does not offer is skipped with the log warning "Could not find dependency", and the plugin will then fail to load. On a controller without access to an update center, install the four dependencies first.

### B) Copy the file into the plugins directory

Copy `devbuild.hpi` to `$JENKINS_HOME/plugins/devbuild.jpi` and restart Jenkins. Jenkins does not install dependencies in this case, so make sure the plugins listed under Requirements are installed.

### C) Docker image

```dockerfile
FROM jenkins/jenkins:2.555.3-lts-jdk21
RUN jenkins-plugin-cli --plugins cloudbees-folder credentials plain-credentials structs workflow-aggregator
COPY devbuild.hpi /usr/share/jenkins/ref/plugins/devbuild.jpi
```

### D) Build from source

The build uses Gradle (wrapper included) and the Jenkins Gradle JPI plugin. You need Java 21.

```sh
./gradlew build          # compile, run all tests and checks, produce build/libs/devbuild.hpi
./gradlew jpi            # only produce build/libs/devbuild.hpi, without tests
./gradlew server         # start a local Jenkins on http://localhost:8080 with the plugin installed (data in ./work)
```

Set the version of a release build with `-Pversion=1.2.3`; the default is `1.0.0-SNAPSHOT`.

## Discord setup

### Create a webhook

1. In Discord, open **Server Settings > Integrations > Webhooks** (or **Edit Channel > Integrations > Webhooks** for a single channel) and click **New Webhook**.
2. Pick the channel, optionally set a name and avatar, and click **Copy Webhook URL**. The URL looks like `https://discord.com/api/webhooks/<id>/<token>`. Anyone with the URL can post to the channel, so treat it as a secret.

### Store the URL as a credential

In Jenkins, open **Manage Jenkins > Credentials** (or the credentials page of a folder), add a credential of kind **Secret text**, paste the webhook URL as the secret and give it an id such as `discord-devbuilds`. Use that id as `credentialsId`. Folder credentials can be used by jobs in that folder.

You can also paste the URL into the **Webhook URL** field of a webhook. It is stored encrypted, but a credential is easier to rotate and to share between jobs.

### Post into a thread or forum post

Turn on **User Settings > Advanced > Developer Mode** in Discord, right click a thread or forum post and choose **Copy Thread ID**. Put the number into `threadId` and every build is posted into that thread.

### Forum channels

A webhook in a forum (or media) channel must either post into an existing post (`threadId`) or create a new post. Set `threadName` to create a new forum post for every build, for example `Build ${BUILD_NUMBER} of ${PROJECT_NAME}`. `appliedTags` takes up to five comma separated forum tag ids; copy a tag id with Developer Mode on from the forum's tag settings. The files that follow the embed in their own messages are posted into the new forum post, not into a second one.

### Upload size limits

Discord limits the size of uploads per message. Servers without boosts accept about 10 MiB, boosted servers more, and a whole request may not exceed 25 MiB. `maxUploadSizeMb` (default 10) is the budget per message: files are spread over follow-up messages to stay under it, and a single file larger than the budget is handled by `oversizePolicy`. Raise the budget only if your server allows larger uploads; if Discord answers HTTP 413, lower it.

## Configuration

Settings are grouped into sections: the webhook, `message`, `embed`, `artifacts` and `conditions`. A section that is not set at one level is inherited as a whole from the next level.

### Inheritance order

From highest to lowest priority:

1. The `discordDevBuild` step or the post-build action.
2. The job property (`discordDevBuildJob`).
3. Folder properties (`discordDevBuildFolder`), from the nearest folder up to the top. Multibranch projects and Organization Folders are folders, so a property on them applies to all their branch jobs.
4. The global configuration in **Manage Jenkins > System > DevBuild Discord Publisher**.
5. The built-in defaults listed in the reference below.

For the webhook, the first level that has an inline `webhook` with a credential or URL, or a `webhookName`, wins. At the same level an inline webhook wins over `webhookName`. An inline webhook without a credential or URL only overrides the thread settings (`threadId`, `threadName`, `appliedTags`) and inherits the URL from further down, which is handy for pointing one job at its own thread. The global level contributes `defaultWebhookName`. If no level provides a webhook, the step fails with a message saying so.

### Global configuration

Open **Manage Jenkins > System > DevBuild Discord Publisher**. Add one or more named webhooks, choose the default webhook, and optionally set default message, embed, attachment and notify settings for all jobs. The **Test webhook** button sends a test message with the values in the form.

### Freestyle jobs

Add the post-build action **Post build to Discord (DevBuild)**. Archive your jars with **Archive the artifacts** in the same job; Jenkins runs the archiver before the Discord action. Without further settings it posts to the default webhook with the default embed and uploads `**/*.jar`.

Alternatively, enable **DevBuild Discord Publisher** in the job's properties (or on a parent folder) to post every build without a post-build action.

### Pipeline (scripted)

```groovy
node {
    checkout scm
    sh './gradlew build'
    archiveArtifacts 'build/libs/*.jar'
    discordDevBuild webhookName: 'devbuilds',
        artifacts: [includes: 'build/libs/*.jar'],
        embed: [title: 'Dev build ${BUILD_NUMBER}', description: '${CHANGES}', showChanges: false]
}
```

Use single quotes for templates so Groovy does not expand `${...}` itself. The step runs inside or outside `node`. Outside `node` it uses archived artifacts. Inside `node` it falls back to workspace files when nothing matching was archived (`source: 'AUTO'`), or reads the workspace only with `artifacts: [source: 'WORKSPACE']`.

When the step runs before the build has finished, the build result is usually not final yet. The step then reports the current result, or `SUCCESS` when none is set. Pass `result:` to report something else, for example in a `catch` block:

```groovy
try {
    sh './gradlew build'
} catch (err) {
    discordDevBuild webhookName: 'devbuilds', result: 'FAILURE'
    throw err
}
```

### Pipeline (Declarative)

```groovy
pipeline {
    agent any
    stages {
        stage('Build') {
            steps {
                sh './gradlew build'
                archiveArtifacts 'build/libs/*.jar'
            }
        }
    }
    post {
        success {
            discordDevBuild webhookName: 'devbuilds', message: [content: '<@&123456789012345678> new dev build']
        }
        failure {
            discordDevBuild webhookName: 'devbuilds', result: 'FAILURE'
        }
    }
}
```

The step also works in a `post` section of a pipeline with `agent none`, where no workspace is available.

### Multibranch Pipeline and Organization Folders without Jenkinsfile changes

Open the Multibranch project (or Organization Folder, or any parent folder), click **Configure**, enable **DevBuild Discord Publisher** and choose a webhook. With **Post every completed build automatically** on (the default), every branch build is posted when it completes, with its archived artifacts. Use the branch pattern under the notify conditions to limit posts to some branches, for example `main|release/.*`.

The project name in the default title (`${PROJECT_NAME}`) is the Multibranch project's display name, and the Branch field shows the branch.

### Multibranch Pipeline with a Jenkinsfile

You can also call the step in the Jenkinsfile. When a folder property posts automatically, **Skip the automatic post when the step or post-build action already ran** (`skipIfStepUsed`, on by default) prevents a second post.

```groovy
node {
    checkout scm
    sh './gradlew build'
    archiveArtifacts 'build/libs/*.jar'
    if (env.BRANCH_NAME == 'main') {
        discordDevBuild webhookName: 'devbuilds'
    }
}
```

### Job property from a Pipeline

```groovy
properties([
    discordDevBuildJob(webhookName: 'devbuilds', autoPublish: true,
        embed: [title: '${PROJECT_NAME} ${BUILD_DISPLAY_NAME}'],
        conditions: [notifyOnSuccess: false])
])
```

The property applies to the next builds of the job, like every `properties` call.

### Configuration as Code (JCasC)

```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - string:
              scope: GLOBAL
              id: discord-devbuilds
              secret: "${DISCORD_DEVBUILDS_WEBHOOK}"
unclassified:
  discordDevBuild:
    defaultWebhookName: devbuilds
    timeoutSeconds: 30
    maxRetries: 3
    webhooks:
      - name: devbuilds
        webhook:
          credentialsId: discord-devbuilds
      - name: releases
        webhook:
          credentialsId: discord-devbuilds
          threadName: "Release ^${BUILD_NUMBER}"
          appliedTags: "111111111111111111"
    message:
      username: "Jenkins"
    embed:
      footerText: "Built by Jenkins"
      fields:
        - name: "Version"
          value: "^${VERSION}"
          inline: true
    artifacts:
      includes: "build/libs/*.jar"
    conditions:
      notifyOnAborted: true
```

JCasC resolves `${...}` itself, so escape plugin templates as `^${...}`.

## Reference

Option names are the same in the UI, in Pipeline and in JCasC. Templates are marked; see [Template variables](#template-variables).

### Step and post-build action: `discordDevBuild`

| Option | Default | Description |
| --- | --- | --- |
| `webhookName` | inherited | Name of a webhook from the global configuration. |
| `webhook` | inherited | A webhook defined here (see Webhook). |
| `message` | inherited | Message settings section. |
| `embed` | inherited | Embed settings section. |
| `artifacts` | inherited | Attachment settings section. |
| `conditions` | inherited | Notify conditions section. |
| `result` | current result, or `SUCCESS` | Result to report: `SUCCESS`, `UNSTABLE`, `FAILURE`, `ABORTED` or `NOT_BUILT`. |

Configuration errors (no webhook, an unknown `webhookName`, a missing credential, `oversizePolicy: 'FAIL'`, an unknown `result`) fail the step. Discord being unreachable or rejecting the request is logged and does not fail the build. The step only needs a workspace when its own `artifacts.source` is `WORKSPACE`.

### Job property `discordDevBuildJob` and folder property `discordDevBuildFolder`

Both accept `webhookName`, `webhook`, `message`, `embed`, `artifacts` and `conditions` as above, plus:

| Option | Default | Description |
| --- | --- | --- |
| `autoPublish` | `true` | Post every completed build automatically. Only archived artifacts are attached. |
| `skipIfStepUsed` | `true` | Skip the automatic post when the step or post-build action already ran for the build. |

Whether a build is posted automatically is decided by the nearest property: the job property if the job has one, otherwise the nearest folder property.

### Global configuration `discordDevBuild`

| Option | Default | Description |
| --- | --- | --- |
| `webhooks` | none | List of named webhooks (`name`, `webhook`). Names use letters, digits, `.`, `_` and `-`. |
| `defaultWebhookName` | none | Webhook used when no other level names one. |
| `message`, `embed`, `artifacts`, `conditions` | built-in defaults | Default sections for all jobs. |
| `timeoutSeconds` | `30` | Connect and response timeout for each Discord request. |
| `maxRetries` | `3` | Retries after HTTP 429 (waiting as long as Discord asks, at most 60 seconds per wait), HTTP 5xx and connection failures. HTTP 401, 403 and 404 are never retried. |

### Webhook (`webhook`)

| Option | Default | Description |
| --- | --- | --- |
| `credentialsId` | none | Secret text credential holding the webhook URL. Preferred. |
| `webhookUrl` | none | The webhook URL, stored encrypted. Used when no credential is set. Not settable from Pipeline scripts; use `credentialsId` there. |
| `threadId` | none | Existing thread or forum post to post into. |
| `threadName` | none | Template. Title of a new forum post created per build. Ignored when `threadId` is set. |
| `appliedTags` | none | Comma separated forum tag ids for the new forum post (at most 5). |

### Message (`message`)

| Option | Default | Description |
| --- | --- | --- |
| `content` | empty | Template. Text above the embed, up to 2000 characters. May contain mentions such as `<@&roleId>` or `<@userId>`. |
| `username` | webhook name | Template. Display name for the post. Discord rejects names containing "discord". |
| `avatarUrl` | `https://cdn.moonrise.gg/api/cdn/download/images/jenkins-icon.png` (the Jenkins icon) | Template. Avatar image URL for the post; it does not change the embed thumbnail. Set it to an empty string to use the webhook's own avatar. Jobs without a message section get the Jenkins icon, and so does a saved message section that has no `avatarUrl` entry. A message section saved by an earlier version of the plugin stored an empty avatar, which still means the webhook's own avatar; enter a URL there to show the icon. |
| `tts` | `false` | Send as text-to-speech. Not available with the default `placement` of `IN_EMBED`, because Discord does not allow text-to-speech for messages laid out with components; follow-up messages are never sent as text-to-speech. |
| `suppressNotifications` | `false` | Post silently (no push or desktop notification). |
| `mentionUsers` | `true` | User mentions in `content` ping the user. |
| `mentionRoles` | `true` | Role mentions in `content` ping the role. |
| `mentionEveryone` | `false` | `@everyone` and `@here` ping. |

### Embed (`embed`)

| Option | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Add the embed. Without it, files that were not uploaded are listed in the message text. |
| `title` | `${PROJECT_NAME} #${BUILD_NUMBER}` | Template, up to 256 characters. |
| `description` | empty | Template, up to 4096 characters. |
| `url` | `${BUILD_URL}` | Template. Link on the title. |
| `authorName`, `authorUrl`, `authorIconUrl` | empty | Templates. Author line above the title. |
| `thumbnailUrl` | `https://cdn.moonrise.gg/api/cdn/download/images/Moonrise%20Studios%20Logo%20%281%29.png` (the Moonrise Studios logo) | Template. Small image in the top right corner. Set it to an empty string for no thumbnail. Jobs without an embed section get the logo, and so does a saved embed section that has no `thumbnailUrl` entry. An embed section saved by an earlier version of the plugin stored an empty thumbnail, which still means no thumbnail; enter a URL there to show one. |
| `imageUrl` | empty | Template. Large image below the fields. |
| `footerText`, `footerIconUrl` | empty | Templates. Footer line. |
| `timestamp` | `true` | Show when the build finished. |
| `successColor` | `#2ECC71` | Colour for `SUCCESS`. |
| `unstableColor` | `#F1C40F` | Colour for `UNSTABLE`. |
| `failureColor` | `#E74C3C` | Colour for `FAILURE`. |
| `abortedColor` | `#95A5A6` | Colour for `ABORTED`. |
| `notBuiltColor` | `#95A5A6` | Colour for `NOT_BUILT`. |
| `showResult` | `true` | Result field. |
| `showDuration` | `true` | Duration field. |
| `showBranch` | `true` | Branch field, when `BRANCH_NAME` is set. |
| `showCommit` | `true` | Commit field with the short `GIT_COMMIT`, when set. |
| `showChanges` | `true` | Changes field with the change log. |
| `maxChanges` | `5` | Number of changes listed. |
| `showArtifacts` | `true` | Artifacts field with file names and sizes, linked to Jenkins when archived. With the default `placement` of `IN_EMBED`, files shown as download cards in the card are left out of the field, so it only lists files that were not attached or that follow in later messages, and it is omitted when there are none. |
| `fields` | none | Custom fields: list of `[name: ..., value: ..., inline: true/false]`; name and value are templates. |

Text that exceeds a Discord limit is cut and ends with "…". When the whole embed exceeds 6000 characters, the description is shortened first, then trailing fields are dropped.

### Attachments (`artifacts`)

| Option | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Upload files. |
| `includes` | `**/*.jar` | Comma separated Ant patterns of files to upload. |
| `excludes` | `**/*-sources.jar, **/*-javadoc.jar` | Comma separated Ant patterns to leave out. |
| `source` | `AUTO` | `AUTO`: archived artifacts, or workspace files if none match and a workspace is available. `ARCHIVED`: archived artifacts only. `WORKSPACE`: workspace files only. |
| `maxFiles` | `10` | Maximum number of files uploaded; further files are only listed in the embed. |
| `maxUploadSizeMb` | `10` | Upload budget per message in MiB (1 to 25). |
| `onlyOnSuccess` | `true` | Upload files only for successful builds. Other builds still list the files. |
| `attachOnUnstable` | `false` | With `onlyOnSuccess`, also upload for unstable builds. |
| `oversizePolicy` | `LINK` | For a file larger than the budget: `LINK` lists it with a link to the Jenkins artifact, `SKIP` leaves it out, `FAIL` fails the step (automatic posts log the error instead). |
| `placement` | `IN_EMBED` | Where the files go relative to the embed. `IN_EMBED` posts one message that lays the embed out with Discord components: a card with a coloured accent bar, the fields as lines of text, and the files as download cards under a "Download here" heading inside it. Leading inline fields (result, duration, branch, commit) sit next to the thumbnail when the header has room. `AFTER_EMBED` posts a classic embed first and the files in follow-up messages right after it, so they appear below the embed. `WITH_EMBED` attaches the first files to the classic embed message itself. Discord always renders a classic message's attachments above its embeds, so with `WITH_EMBED` the files appear above the embed. |

Files are sorted by path and uploaded in batches of up to 10 files within the budget. With the default `placement: 'IN_EMBED'` the first batch is shown inside the card, and further batches follow in their own messages. Uploaded file names are reduced to letters, digits, `.`, `_` and `-` (other characters become `_`) so the card can refer to them. With `placement: 'AFTER_EMBED'` the first message carries the message text and the embed, and every batch follows in its own message. With `placement: 'WITH_EMBED'` the first batch is attached to the embed message and further batches follow. The follow-up messages have no embed and no message text (so mentions do not ping twice), use the same username and avatar so Discord groups them under the post, and go to the same channel, thread or forum post. When the embed is turned off or there are no files to upload, a single message is posted. If the first message fails, no files are sent; a failed follow-up is logged like any other Discord error. Files are streamed from the controller or the agent and are not loaded into memory.

### Notify conditions (`conditions`)

| Option | Default | Description |
| --- | --- | --- |
| `notifyOnSuccess` | `true` | Post successful builds. |
| `notifyOnUnstable` | `true` | Post unstable builds. |
| `notifyOnFailure` | `true` | Post failed builds. |
| `notifyOnAborted` | `false` | Post aborted builds. |
| `notifyOnNotBuilt` | `false` | Post `NOT_BUILT` builds. |
| `notifyOnBackToNormal` | `true` | Post a successful build after a build that was not successful, even when `notifyOnSuccess` is off. |
| `branchPattern` | empty | Regular expression the whole `BRANCH_NAME` must match. Ignored when `BRANCH_NAME` is not set. |

### Template variables

Templates replace `${NAME}` and `$NAME` with build variables. Unknown variables are left as they are. Every environment variable of the build is available (`BUILD_NUMBER`, `BUILD_URL`, `JOB_NAME`, `BRANCH_NAME`, `GIT_COMMIT`, `CHANGE_ID` and so on), plus these, which take precedence:

| Variable | Value |
| --- | --- |
| `PROJECT_NAME` | For Multibranch branch jobs the Multibranch project's display name, otherwise the job's display name. |
| `JOB_DISPLAY_NAME` | Display name of the job (the branch name for branch jobs). |
| `FULL_DISPLAY_NAME` | Full display name of the build, e.g. `team » app #12`. |
| `BUILD_DISPLAY_NAME` | Display name of the build, e.g. `#12`. |
| `BUILD_RESULT` | `SUCCESS`, `UNSTABLE`, `FAILURE`, `ABORTED` or `NOT_BUILT`. |
| `BUILD_DURATION` | Human readable duration, e.g. `1 min 3 sec`. |
| `ARTIFACTS` | Comma separated names of the uploaded files. |
| `CHANGES` | The change log as Markdown lines (up to `maxChanges`). |
| `COMMIT_SHORT` | First 7 characters of `GIT_COMMIT`, or empty. |
| `BRANCH` | `BRANCH_NAME`, or empty. |

`BUILD_URL` needs the Jenkins URL to be set in **Manage Jenkins > System**; without it embeds have no title link and artifacts are not linked.

## Troubleshooting

Every action is logged in the build log with the prefix `[DevBuild]`. The webhook URL is shown masked, e.g. `https://discord.com/api/webhooks/1234…/****`.

- **Nothing is posted.** Look for `[DevBuild] Not posting to Discord:` in the log; it names the condition that ruled the build out. For automatic posts, check that the nearest job or folder property has `autoPublish` on.
- **"No Discord webhook is configured".** Set `webhookName` or `webhook` on the step, job or a folder, or choose a default webhook in the global configuration.
- **HTTP 404.** The webhook was deleted or the thread id is wrong. Create a new webhook and update the credential.
- **HTTP 401.** The webhook token is invalid; copy the URL again.
- **HTTP 400 in a forum channel.** Forum webhooks need `threadId` or `threadName`.
- **HTTP 413.** The upload is too large for the server; lower `maxUploadSizeMb`.
- **HTTP 429.** Discord rate limits the webhook. The plugin waits and retries up to `maxRetries` times. Many jobs sharing one webhook can hit the limit; use several webhooks.
- **Files are missing.** Automatic posts only see archived artifacts, so archive them with `archiveArtifacts`. Check `includes` and `excludes`, and `onlyOnSuccess` for unstable or failed builds.
- **The step fails outside `node` with a missing workspace.** `artifacts.source` is `WORKSPACE`; use `AUTO` or `ARCHIVED`, or call the step inside `node`.
- **Posted twice.** The step ran and a job or folder property posted automatically with `skipIfStepUsed` off.
- **Test webhook button.** It needs Configure permission on the job or folder, or Administer in the global configuration.

## License

MIT, see [LICENSE](LICENSE).
