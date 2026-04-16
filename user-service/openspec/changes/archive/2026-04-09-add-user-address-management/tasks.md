## 1. Schema And Mapper

- [x] 1.1 扩展 `user_address` 表结构，补齐收件信息、默认地址、更新时间和软删除字段，并保持 `address_id` 为自增主键
- [x] 1.2 扩展 `UserAddressMapper` 与 XML，支持有效地址计数、列表查询、按地址 ID 查询、插入、更新、软删除、默认地址清理和默认补位候选查询
- [x] 1.3 更新 schema 与 mapper 相关测试，验证新字段、SQL 映射和有效地址查询约束

## 2. Address Domain And Service

- [x] 2.1 新增地址实体、服务接口和默认服务实现，封装地址创建、更新、删除、列表查询和所有权查询逻辑
- [x] 2.2 在地址服务中实现数量上限、默认地址唯一性、首地址自动默认和删除默认地址自动补位规则
- [x] 2.3 在地址服务中统一处理不存在地址与越权地址的最小披露语义，并补充对应单元测试或工作流测试

## 3. HTTP API And Error Handling

- [x] 3.1 新增地址请求/响应模型和 `AddressController`，提供 `/api/v1/addresses` 的列表、新增、整单更新和删除接口
- [x] 3.2 调整安全配置，将地址接口纳入现有认证保护，并让成功写入返回完整地址对象
- [x] 3.3 扩展统一异常处理，暴露 `ADDRESS_LIMIT_EXCEEDED` 等地址模块业务错误码

## 4. Protected Write Integration

- [x] 4.1 让现有 `/api/v1/protected/address/write` 复用共享地址所有权查询逻辑，而不是直接依赖占位式 mapper 查询
- [x] 4.2 保持上传相关策略不变，并更新受保护写入契约测试以覆盖真实地址资源语义

## 5. Verification

- [x] 5.1 补充地址请求校验测试，覆盖必填字段、手机号格式、长度限制和宽松邮编规则
- [x] 5.2 补充地址控制器和服务工作流测试，覆盖新增、更新、删除、列表、默认地址切换、默认补位和数量上限
- [x] 5.3 运行相关测试套件，确认 `user-address` 与 `api-risk-control` 规范对应行为全部通过
