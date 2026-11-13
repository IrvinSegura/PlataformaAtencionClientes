package com.irvinsegura.supportchat.shared.demo;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The workspace behind the public demo, if this platform runs one. Anyone on the internet asks there on the platform's
 * account, so it always answers with the fast, economical model and stops for the day after a number of answers.
 *
 * @param workspaceId  the demo workspace, or null when there is no demo
 * @param dailyAnswers answers it gives per day (UTC) before handing the rest to the team, as a plan at its limit does
 */
@ConfigurationProperties("supportchat.demo")
public record DemoWorkspace(UUID workspaceId, int dailyAnswers) {

    public boolean is(UUID workspaceId) {
        return this.workspaceId != null && this.workspaceId.equals(workspaceId);
    }
}
