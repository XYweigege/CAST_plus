package com.voc.insight.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果封装。
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> implements Serializable {

    /** 总记录数 */
    @Schema(description = "总记录数", example = "128")
    private Long total;

    /** 总页数 */
    @Schema(description = "总页数", example = "7")
    private Long pages;

    /** 当前页码 */
    @Schema(description = "当前页码", example = "1")
    private Long current;

    /** 每页条数 */
    @Schema(description = "每页条数", example = "20")
    private Long size;

    /** 当前页记录 */
    @Schema(description = "当前页记录")
    private List<T> records;

    public static <T> PageResult<T> of(Long total, Long current, Long size, List<T> records) {
        PageResult<T> page = new PageResult<>();
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        page.setPages(size > 0 ? (total + size - 1) / size : 0);
        page.setRecords(records);
        return page;
    }
}
