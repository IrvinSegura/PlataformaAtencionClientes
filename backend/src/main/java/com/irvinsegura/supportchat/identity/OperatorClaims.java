package com.irvinsegura.supportchat.identity;

/**
 * Claims of an operator's access token, besides the standard ones ({@code sub} is the user id).
 */
public final class OperatorClaims {

    /**
     * The workspace the token was issued for; every request made with it runs in that workspace.
     */
    public static final String WORKSPACE = "wid";

    /**
     * The operator's role in that workspace, as a single-element list.
     */
    public static final String ROLES = "roles";

    private OperatorClaims() {
    }
}
