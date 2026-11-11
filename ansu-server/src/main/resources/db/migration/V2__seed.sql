-- 安宿 · 演示种子
-- 1 栋楼、20 间宿舍（id = 房间号，含 314/315）、六角色演示身份。
-- 密码暂为空：当前用 /api/auth/demo 演示身份切换；全量账号密码在 P1 补。

INSERT INTO building (id, school_id, code, name, free_kwh_per_person, price_per_kwh)
VALUES (1, 1, 'B1', '1 号楼', 8.0, 0.570);

INSERT INTO dorm (id, school_id, building_id, room_no, floor, capacity,
                  breaker_rating_w, policy_limit_w, wash_limit_w, night_limit_w,
                  status, credit_score)
VALUES
  (101, 1, 1, '101', 1, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (102, 1, 1, '102', 1, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (103, 1, 1, '103', 1, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (104, 1, 1, '104', 1, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (105, 1, 1, '105', 1, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (201, 1, 1, '201', 2, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (202, 1, 1, '202', 2, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (203, 1, 1, '203', 2, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (204, 1, 1, '204', 2, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (205, 1, 1, '205', 2, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (311, 1, 1, '311', 3, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (312, 1, 1, '312', 3, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (313, 1, 1, '313', 3, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (314, 1, 1, '314', 3, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (315, 1, 1, '315', 3, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (401, 1, 1, '401', 4, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (402, 1, 1, '402', 4, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (403, 1, 1, '403', 4, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (404, 1, 1, '404', 4, 4, 2200, 1500, 2000, 400, 'normal', 100),
  (405, 1, 1, '405', 4, 4, 2200, 1500, 2000, 400, 'normal', 100);

INSERT INTO user_account (school_id, username, password_hash, role, display_name, dorm_id, status)
VALUES
  (1, 'student314', NULL, 'student', '314 宿舍', 314, 'active'),
  (1, 'student315', NULL, 'student', '315 宿舍', 315, 'active'),
  (1, 'manager01',  NULL, 'manager', '宿管 01', NULL, 'active'),
  (1, 'admin01',    NULL, 'admin',   '管理员 01', NULL, 'active'),
  (1, 'counselor01',NULL, 'counselor','辅导员 01', NULL, 'active'),
  (1, 'repair01',   NULL, 'repair',  '维修 01', NULL, 'active'),
  (1, 'guest01',    NULL, 'guest',   '评委访客', NULL, 'active');
