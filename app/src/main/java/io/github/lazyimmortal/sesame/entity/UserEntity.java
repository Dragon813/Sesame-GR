package io.github.lazyimmortal.sesame.entity;

import lombok.Data;
import lombok.Getter;
import io.github.lazyimmortal.sesame.util.StringUtil;

@Getter
public class UserEntity {

    private final String userId;

    private final String account;

    private final Integer friendStatus;

    private final String realName;

    private final String nickName;

    private final String remarkName;

    private final String showName;

    private final String maskName;

    private final String fullName;

    private final String headImageUrl;

    private final String firstAlphaChar;

    private final Long friendTime;

    private final Long registerTime;

    private final Boolean isDelete;

    public UserEntity(String userId, String account, Integer friendStatus, String realName, String nickName, String remarkName) {
        this(userId, account, friendStatus, realName, nickName, remarkName, null, null, null, null, null);
    }

    public UserEntity(String userId, String account, Integer friendStatus, String realName, String nickName, String remarkName,
                      String headImageUrl, String firstAlphaChar, Long friendTime, Long registerTime, Boolean isDelete) {
        this.userId = userId;
        this.account = account;
        this.friendStatus = friendStatus;
        this.realName = realName;
        this.nickName = nickName;
        this.remarkName = remarkName;
        this.headImageUrl = headImageUrl;
        this.firstAlphaChar = firstAlphaChar;
        this.friendTime = friendTime;
        this.registerTime = registerTime;
        this.isDelete = isDelete;
        String showNameTmp;
        if (StringUtil.isEmpty(remarkName)) {
            showNameTmp = nickName;
        } else {
            showNameTmp = remarkName;
        }
        String maskNameTmp;
        if (realName != null && realName.length() > 1) {
            maskNameTmp = "*" + realName.substring(1);
        } else {
            maskNameTmp = realName;
        }
        /*if (isMaskAccount) {
            int length = account.length();
            if (length > 5) {
                int prefixIndex = Math.min(3, Math.max(1, length - 3));
                String prefix = account.substring(0, prefixIndex);
                int tmpIndex = prefixIndex + 3;
                int suffixIndex = length - 4;
                if (suffixIndex < tmpIndex) {
                    suffixIndex = tmpIndex;
                }
                String suffix = account.substring(suffixIndex);
                account = prefix + "***" + suffix;
            }
        }*/
        this.showName = showNameTmp;
        this.maskName = showNameTmp + "|" + maskNameTmp;
        this.fullName = showNameTmp + "|" + realName + "(" + account + ")";
    }

    @Data
    public static class UserDto {

        private String userId;

        private String account;

        private Integer friendStatus;

        private String realName;

        private String nickName;

        private String remarkName;

        private String showName;

        private String headImageUrl;

        private String firstAlphaChar;

        private Long friendTime;

        private Long registerTime;

        private Boolean isDelete;

        public UserEntity toEntity() {
            return new UserEntity(userId, account, friendStatus, realName, nickName, remarkName,
                    headImageUrl, firstAlphaChar, friendTime, registerTime, isDelete);
        }

    }
}
