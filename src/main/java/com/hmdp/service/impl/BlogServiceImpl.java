package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.xml.ws.Holder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    IUserService userService;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    IFollowService followService;

    @Override
    public Result queryByBlogDetail(Long id) {
        Blog blog = getById(id);
        setBlogUser(blog);
        // 设置点没点赞字段给前端
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        if (UserHolder.getUser() == null) {
            return;
        }
        Long userid = UserHolder.getUser().getId();
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userid.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result queryMyBlog(Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            setBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userid = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userid.toString());
        if (score != null) {
            // 已点赞过，liked-1
            boolean update = update().setSql("liked = liked - 1").eq("id", id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().remove(key, userid.toString());
            }
        } else {
            // 未点赞过，liked+1
            boolean update = update().setSql("liked = liked + 1").eq("id", id).update();
            if (update) {
                stringRedisTemplate.opsForZSet().add(key, userid.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryLikes(Long id) {
        String key = "blog:liked:" + id;
        // 从SortSet中查询top5
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (range == null || range.isEmpty()) {
            // 没有用户点赞
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> userDTOS = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        // 推送到每一个粉丝的收件箱中
        if (isSuccess) {
            // 获取粉丝用户
            List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
            for (Follow follow : follows) {
                String key = "feed:" + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
            }
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryByBlogOfFollow(Long lastMin, Integer offset) {
        Long id = UserHolder.getUser().getId();
        String key = "feed:" + id.toString();
        // ZREVRANGEBYSCORE key max min [WITHSCORES] [LIMIT offset count]
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, lastMin, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(new ScrollDTO());
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        // 在SortedSet，根据从大到小的顺序查出数据
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue()));
            long time = typedTuple.getScore().longValue();
            if(minTime == time) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        // 查出blog
        String idStr = StrUtil.join(", ", ids);
        // 使用last自定义sql语句，使用in查询不会保证有序
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 添加blog中的数据
        for (Blog blog : blogs) {
            setBlogUser(blog);
            isBlogLiked(blog);
        }
        ScrollDTO scrollDTO = new ScrollDTO();
        scrollDTO.setList(blogs);
        scrollDTO.setOffset(os);
        scrollDTO.setMinTime(minTime);
        return Result.ok(scrollDTO);
    }

    private void setBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
