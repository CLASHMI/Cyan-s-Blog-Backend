package com.loopia.gameanimationshow.controller;

import com.loopia.gameanimationshow.common.Result;
import com.loopia.gameanimationshow.model.vo.WeatherVO;
import com.loopia.gameanimationshow.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import com.loopia.gameanimationshow.model.vo.WeatherCityVO;
import java.util.List;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping("/cities")
    public Result<List<WeatherCityVO>> cities(@RequestParam String keyword) {
        return Result.success(weatherService.cities(keyword));
    }

    public record CitySelection(String id) {}

    @PutMapping("/city")
    public Result<WeatherVO> selectCity(@RequestBody CitySelection selection) {
        return Result.success(weatherService.selectCity(selection.id()));
    }

    @GetMapping("/current")
    public Result<WeatherVO> current() {
        return Result.success(weatherService.current());
    }
}
