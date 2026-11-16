/**
 * Workspaces (tenants): who belongs to each one, and how its chat widget is set up (public key, allowed web origins,
 * assistant name and greeting). Other modules use the {@link com.irvinsegura.supportchat.workspaces.Workspaces} API.
 */
@ApplicationModule(displayName = "Workspaces")
package com.irvinsegura.supportchat.workspaces;

import org.springframework.modulith.ApplicationModule;
