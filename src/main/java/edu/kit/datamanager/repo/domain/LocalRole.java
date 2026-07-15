package edu.kit.datamanager.repo.domain;

public enum LocalRole {
    USER("ROLE_USER"),
    CURATOR("ROLE_CURATOR"),
    ADMINISTRATOR("ROLE_ADMINISTRATOR");

    private final String authority;

    LocalRole(String authority) { this.authority = authority; }
    public String authority() { return authority; }
}
