package nju.researchfun.entity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeChatWebModel {
    private String access_token;    //  接口调用凭证
    private String expires_in;      //  access_token接口调用凭证超时时间
    private String refresh_token;   //	用户刷新access_token
    private String openid;          //	授权用户唯一标识
    private String scope;           //	用户授权的作用域，使用逗号（,）分隔
    private String unionid;         //	当且仅当该网站应用已获得该用户的userinfo授权时，才会出现该字段。

    private String errcode;         //  用于错误情况
    private String errmsg;          //  用于错误情况

}