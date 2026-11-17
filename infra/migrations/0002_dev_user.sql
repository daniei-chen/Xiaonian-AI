-- P0 开发期固定用户，跳过登录流程。
-- 生产环境必须替换为真实注册流程（见 spec §8.1）。

INSERT INTO users (id, phone_or_email, locale, timezone, plan)
VALUES (
  '00000000-0000-4000-8000-000000000001',
  'dev@xiaonian.local',
  'zh-CN',
  'Asia/Shanghai',
  'free'
)
ON CONFLICT (id) DO NOTHING;
