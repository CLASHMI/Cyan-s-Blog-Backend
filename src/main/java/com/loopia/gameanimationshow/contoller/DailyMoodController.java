package com.loopia.gameanimationshow.contoller;

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.model.vo.DailyMoodVO;
import com.loopia.gameanimationshow.service.DailyMoodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mood")
@RequiredArgsConstructor
public class DailyMoodController {
    private final DailyMoodService dailyMoodService;

    @GetMapping("/today")
    public Result<DailyMoodVO> today() {
        return Result.success(dailyMoodService.today(), "今日心情");
    }

    // 与公开读取分开路径，写接口始终经过身份拦截器和服务层管理员校验。
    @PostMapping("/select")
    public Result<DailyMoodVO> select(@Valid @RequestBody Selection selection) {
        return Result.success(dailyMoodService.select(selection.mood()), "心情已记录");
    }

    public record Selection(@NotBlank String mood) {}
}
