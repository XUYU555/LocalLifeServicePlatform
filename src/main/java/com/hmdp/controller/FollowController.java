package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author xy
 * @since 2024-4-25 20:45
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;


    @GetMapping(value = "/or/not/{followUserId}")
    public Result isFollow(@PathVariable Long followUserId) {
        return followService.isFollow(followUserId);
    }

    @PutMapping(value = "/{followUserId}/{follow}")
    public Result follow(@PathVariable Long followUserId, @PathVariable Boolean follow) {
        return followService.follow(followUserId, follow);
    }

    @GetMapping(value = "/common/{id}")
    public Result commonFollow(@PathVariable Long id) {
        return followService.commonFollow(id);
    }
}
