-- 教师邀请码表
CREATE TABLE IF NOT EXISTS `teacher_invitation_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(20) NOT NULL COMMENT '邀请码',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint(20) NOT NULL COMMENT '创建人ID',
  `used_by` bigint(20) DEFAULT NULL COMMENT '使用人ID',
  `used_at` timestamp NULL DEFAULT NULL COMMENT '使用时间',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师邀请码表'; 