package cn.yifan.drawsee.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @FileName InvitationCode
 * @Description
 * @Author yifan
 * @date 2025-03-25 08:45
 **/

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InvitationCode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private Timestamp createdAt;

    private Long usedBy;

    private Timestamp usedAt;

    private Boolean isActive;

    private String sentUserName;

    private String sentEmail;

    private Timestamp lastSentAt;

    public InvitationCode(String code) {
        this.code = code;
    }

}
