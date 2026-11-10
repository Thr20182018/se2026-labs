-- 虚构演示数据。密码均为 Demo-2026，库中只保存 BCrypt 哈希。
-- 先执行 V001。查询可见性按 2026-11-10 当天设计。

INSERT INTO users (id, account, password_hash, display_name, role) VALUES
  (1, 'lin_zy', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '林知远', 'PARTICIPANT'),
  (2, 'chen_an', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '陈安然', 'PARTICIPANT'),
  (3, 'wang_he', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '王禾', 'PARTICIPANT'),
  (4, 'xu_lin', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '许林', 'PARTICIPANT'),
  (5, 'su_qing', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '苏清和', 'ORGANIZER'),
  (6, 'zhao_ming', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '赵明远', 'ORGANIZER'),
  (7, 'he_qm', '$2b$10$QLJved/YADVPP1F9MwWXneW2DqZ0/Iba1EcvpNcYXrM1knSDUabP6',
    '何启明', 'ADMIN');

INSERT INTO activities (
  id, name, description, organizer_id, publication_status,
  pending_name, pending_description, revision_pending) VALUES
  (1, '蓝晒手作：把植物留在纸上',
    '认识蓝晒的显影过程，用校园植物的轮廓制作一张蓝色手工卡片。',
    5, 'PUBLISHED', NULL, NULL, 0),
  (2, '声音采集：制作你的校园声景',
    '在校园里采集脚步、鸟鸣和风雨，剪成一分钟声景。',
    5, 'PUBLISHED', NULL, NULL, 0),
  (3, '旧物修复：修好你的第一件小物',
    '带上一件小旧物，学习清洁、粘合和基础修复。',
    5, 'PUBLISHED', NULL, NULL, 0),
  (4, '微型生态瓶：装下一片小森林',
    '用瓶子装下泥土、植物和一层薄水，观察封闭生态。',
    5, 'PUBLISHED', NULL, NULL, 0),
  (5, '版画体验：一版一色',
    '用公开简介介绍一版一色的拓印步骤。',
    5, 'PUBLISHED', '待审核名称不应出现', '待审核简介不应出现', 1),
  (6, '草稿木工不应出现', '草稿简介。', 5, 'DRAFT', NULL, NULL, 0),
  (7, '待审核陶艺不应出现', '待审核简介。', 5, 'PENDING', NULL, NULL, 0),
  (8, '驳回书法不应出现', '驳回简介。', 5, 'REJECTED', NULL, NULL, 0),
  (9, '鼓楼夜谈：一本书的晚上',
    '围坐读完一个短篇，交换彼此画下的句子。',
    6, 'PUBLISHED', NULL, NULL, 0);

INSERT INTO sessions (
  id, activity_id, start_time, end_time, registration_deadline,
  location, capacity, publication_status) VALUES
  (1, 1, '2026-11-14 14:00:00', '2026-11-14 16:00:00', '2026-11-13 18:00:00',
    '仙林校区 · 逸夫楼 A203', 8, 'PUBLISHED'),
  (2, 2, '2026-11-15 09:30:00', '2026-11-15 11:30:00', '2026-11-14 18:00:00',
    '仙林校区 · 艺术楼 102', 4, 'PUBLISHED'),
  (3, 3, '2026-11-21 14:00:00', '2026-11-21 16:30:00', '2026-11-08 18:00:00',
    '仙林校区 · 创客空间 201', 6, 'PUBLISHED'),
  (4, 4, '2026-11-22 10:00:00', '2026-11-22 12:00:00', '2026-11-20 18:00:00',
    '仙林校区 · 活动中心 105', 20, 'CANCELLED'),
  (5, 5, '2026-11-18 13:00:00', '2026-11-18 15:00:00', '2026-11-17 18:00:00',
    '仙林校区 · 美术楼 301', 10, 'PUBLISHED'),
  (6, 1, '2026-11-28 14:00:00', '2026-11-28 16:00:00', '2026-11-27 18:00:00',
    '仙林校区 · 逸夫楼 B101', 12, 'PENDING'),
  (7, 6, '2026-11-19 10:00:00', '2026-11-19 12:00:00', '2026-11-18 18:00:00',
    '仙林校区 · 工坊 1', 8, 'DRAFT'),
  (8, 7, '2026-11-19 14:00:00', '2026-11-19 16:00:00', '2026-11-18 18:00:00',
    '仙林校区 · 工坊 2', 8, 'PENDING'),
  (9, 8, '2026-11-19 16:00:00', '2026-11-19 18:00:00', '2026-11-18 18:00:00',
    '仙林校区 · 工坊 3', 8, 'REJECTED'),
  (10, 9, '2026-11-16 19:00:00', '2026-11-16 21:00:00', '2026-11-16 12:00:00',
    '鼓楼校区 · 教学楼 201', 12, 'PUBLISHED');

INSERT INTO registrations (
  id, participant_id, session_id, registered_at, status, checked_in_at,
  waitlist_order) VALUES
  (1, 1, 1, '2026-11-01 09:00:00', 'REGISTERED', NULL, NULL),
  (2, 2, 1, '2026-11-01 09:05:00', 'REGISTERED', NULL, NULL),
  (3, 1, 2, '2026-11-02 09:00:00', 'REGISTERED', NULL, NULL),
  (4, 2, 2, '2026-11-02 09:05:00', 'REGISTERED', NULL, NULL),
  (5, 3, 2, '2026-11-02 09:10:00', 'REGISTERED', NULL, NULL),
  (6, 4, 2, '2026-11-02 09:15:00', 'REGISTERED', NULL, NULL),
  (7, 1, 3, '2026-11-03 09:00:00', 'CHECKED_IN', '2026-11-21 14:05:00', NULL),
  (8, 2, 3, '2026-11-03 09:05:00', 'ABSENT', NULL, NULL),
  (9, 3, 3, '2026-11-03 09:10:00', 'WAITLIST', NULL, 1),
  (10, 4, 3, '2026-11-03 09:15:00', 'CANCELLED', NULL, NULL),
  (11, 1, 5, '2026-11-04 09:00:00', 'REGISTERED', NULL, NULL);
