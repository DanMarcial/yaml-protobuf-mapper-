package io.github.yamlmapper.benchmark.dto;

import java.util.List;

/**
 * Simple DTO for UserEvent benchmark comparison.
 * Field names match the JSON exactly - no transforms needed.
 */
public class UserEventDto {

    private String eventType;
    private String visitorId;
    private String searchQuery;
    private UserInfoDto userInfo;
    private List<String> pageCategories;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getVisitorId() {
        return visitorId;
    }

    public void setVisitorId(String visitorId) {
        this.visitorId = visitorId;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public UserInfoDto getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfoDto userInfo) {
        this.userInfo = userInfo;
    }

    public List<String> getPageCategories() {
        return pageCategories;
    }

    public void setPageCategories(List<String> pageCategories) {
        this.pageCategories = pageCategories;
    }

    public static class UserInfoDto {
        private String userId;
        private String ipAddress;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
    }
}
