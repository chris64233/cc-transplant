package com.chris64233.cc.transplant;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试数据清理器。allocation 与 allocation_entry 之间存在自引用外键
 * （accepted_entry_id），批量删除顺序难以满足约束，因此在 H2 下临时关闭
 * 参照完整性后清空全部业务表。
 */
@Component
public class TestDataCleaner {

    private static final List<String> TABLES = List.of(
            "offer_event", "offer", "allocation_entry", "allocation", "recipient", "donor");

    private final JdbcTemplate jdbcTemplate;

    public TestDataCleaner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void cleanAll() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES) {
                jdbcTemplate.update("DELETE FROM " + table);
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
