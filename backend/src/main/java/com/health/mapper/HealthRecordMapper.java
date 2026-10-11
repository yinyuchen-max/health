package com.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.health.domain.entity.HealthRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface HealthRecordMapper extends BaseMapper<HealthRecord> {
    // 逻辑删除只修改删除标记和版本，避免回写查询快照中的旧健康数据。
    @Update("UPDATE health_record SET deleted = 1, version = version + 1 "
            + "WHERE id = #{id} AND version = #{version} AND deleted = 0")
    int softDeleteWithVersion(@Param("id") Long id, @Param("version") Integer version);
}
