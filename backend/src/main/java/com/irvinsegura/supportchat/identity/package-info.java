/**
 * Operators of the panel: sign-up, sign-in with lockout, short-lived JWT access tokens and rotating refresh tokens
 * with reuse detection, the team of each workspace and invitations to join it. Owns the security configuration of
 * the whole API.
 */
@ApplicationModule(displayName = "Identity")
package com.irvinsegura.supportchat.identity;

import org.springframework.modulith.ApplicationModule;
