package db.cl.gao.common.param;


import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {


    private List<T> data;

    private long total;

    private int page;

    private int size;

    private int totalPages;

}
