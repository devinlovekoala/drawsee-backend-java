package cn.yifan.drawsee.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.yifan.drawsee.pojo.vo.ConversationForkVO;
import cn.yifan.drawsee.pojo.vo.ShareConversationVO;
import cn.yifan.drawsee.service.business.ConversationShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * @FileName ConversationShareController
 * @Description 会话分享控制器
 * @Author devin
 * @date 2026-02-25
 */

@RestController
@RequestMapping("/share")
public class ConversationShareController {

    @Autowired
    private ConversationShareService conversationShareService;

    @GetMapping("/{shareToken}")
    public ShareConversationVO getSharedConversation(@PathVariable String shareToken) {
        return conversationShareService.getShareByToken(shareToken);
    }

    @PostMapping("/{shareToken}/fork")
    @SaCheckLogin
    public ConversationForkVO forkSharedConversation(@PathVariable String shareToken) {
        return conversationShareService.forkSharedConversation(shareToken);
    }
}
