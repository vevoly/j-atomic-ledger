package io.github.vevoly.ledger.core.wal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * WAL分页查询结果 / WAL page query result
 *
 * @since 1.2.3
 * @author vevoly
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalPageResult<T> {

    /**
     * 当前页的数据 / Current page data
     */
    private List<T> records;
    /**
     * 下一页的游标 (WAL Index) / Next page cursor
     */
    private String nextCursor;
    /**
     * 上页的游标 / Previous page cursor
     */
    private String prevCursor;
    /**
     * 是否还有更多数据 / Whether there are more data
     */
    private boolean hasMore;
    /**
     * 是否有上页数据 / Whether there is a previous page
     */
    private boolean hasPrev;
}
