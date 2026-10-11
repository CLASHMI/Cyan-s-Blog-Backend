package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.common.UserContext;
import com.loopia.gameanimationshow.constant.SystemConstant;
import com.loopia.gameanimationshow.service.AiService;
import com.loopia.gameanimationshow.model.vo.DailyQuoteVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AiController 类
 *
 * @author NaNloopia
 * @version 1.0
 * @since 2026/8/7 
 * @created 13:56 
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @GetMapping("/summary")
    public Result<String> getAiSummary(
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh) {
        // 判断当前请求人的角色是不是管理员
        Integer role = UserContext.getRole();
        boolean isAdmin = role != null && SystemConstant.Role.ADMIN.equals(role);

        // refresh=true：身份刚切换，跳过服务端缓存重新生成
        return Result.success(aiService.generateSummary(isAdmin, refresh), "Assistant is online.");
    }

    @GetMapping("/dailyQuote")
    public Result<DailyQuoteVO> dailyQuote() {
        return Result.success(aiService.dailyQuote(), "今日一言");
    }

    // 写操作不复用公开白名单路径，并在服务层再次校验管理员身份。
    @PutMapping("/dailyQuote/manual")
    public Result<DailyQuoteVO> saveDailyQuote(@RequestBody QuoteInput input) {
        return Result.success(aiService.saveDailyQuote(input.text()), "今日一言已保存");
    }

    @PostMapping("/dailyQuote/generate")
    public Result<DailyQuoteVO> generateDailyQuote() {
        return Result.success(aiService.generateDailyQuote(), "今日一言已生成");
    }

    public record QuoteInput(String text) {}

    @GetMapping("/postSummary")
    public Result<String> getPostSummary(@RequestParam("postId") Long postId) {
        /* 单参数 success(String) 会被当成 message，这里必须用双参重载把摘要放进 data */
        return Result.success(aiService.summarizePost(postId), "AI summary is ready.");
    }
}
