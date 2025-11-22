package cn.yifan.drawsee.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName User
 * @Description
 * @Author devin
 * @date 2025-01-27 23:36
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户ID
    private Long id;

    // 用户名
    private String username;

    // 密码
    private String password;

    // 用户创建时间
    private Timestamp createdAt;

    // 用户更新时间
    private Timestamp updatedAt;

    // 用户是否删除
    private Boolean isDeleted;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

}
