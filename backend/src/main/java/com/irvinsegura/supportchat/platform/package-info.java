/**
 * Administration of the platform itself, for whoever runs it rather than for a workspace: every workspace with its
 * owner, plan, usage and activity, and suspending one that abuses the service. Only the users configured as platform
 * administrators reach it; they see counts and contact details, never a workspace's conversations or knowledge.
 */
@ApplicationModule(displayName = "Platform")
package com.irvinsegura.supportchat.platform;

import org.springframework.modulith.ApplicationModule;
