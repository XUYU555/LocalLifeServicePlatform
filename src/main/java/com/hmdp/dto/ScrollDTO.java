package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * @author xy
 * @date 2024-03-23 16:47
 */
@Data
public class ScrollDTO {
    private List<?> list;
    private Integer offset;
    private Long minTime;
}
