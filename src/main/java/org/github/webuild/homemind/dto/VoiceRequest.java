package org.github.webuild.homemind.dto;

public class VoiceRequest {
    private String sessionId;
    private String language;

    // getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
