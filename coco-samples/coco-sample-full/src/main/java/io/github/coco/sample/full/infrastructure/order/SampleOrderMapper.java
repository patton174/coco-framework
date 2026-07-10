package io.github.coco.sample.full.infrastructure.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 示例订单 MyBatis-Plus Mapper。
 *
 * @author patton174
 * @since 1.0.0
 */
@Mapper
public interface SampleOrderMapper extends BaseMapper<SampleOrderEntity> {
}
