-- Schema for the To-Do List & Task Manager.
-- Every statement uses IF NOT EXISTS, so running it on each start never deletes data.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL,
    password_hash VARCHAR(100)  NOT NULL,
    created_at    TIMESTAMP     NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS categories (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT       NOT NULL,
    name    VARCHAR(50)  NOT NULL,
    color   VARCHAR(7)   NOT NULL,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_categories_user_name UNIQUE (user_id, name)
);

-- Tasks and subtasks share one table. A subtask has parent_id set (one level of nesting only).
CREATE TABLE IF NOT EXISTS tasks (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT        NOT NULL,
    parent_id    BIGINT        NULL,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    priority     VARCHAR(10)   NOT NULL,
    due_date     DATE          NULL,
    category_id  BIGINT        NULL,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    completed_at TIMESTAMP     NULL,
    CONSTRAINT fk_tasks_user     FOREIGN KEY (user_id)     REFERENCES users(id)      ON DELETE CASCADE,
    CONSTRAINT fk_tasks_parent   FOREIGN KEY (parent_id)   REFERENCES tasks(id)      ON DELETE CASCADE,
    CONSTRAINT fk_tasks_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT ck_tasks_status   CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT ck_tasks_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX IF NOT EXISTS idx_tasks_user        ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_user_status ON tasks(user_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_user_due    ON tasks(user_id, due_date);
CREATE INDEX IF NOT EXISTS idx_tasks_parent      ON tasks(parent_id);
CREATE INDEX IF NOT EXISTS idx_tasks_category    ON tasks(category_id);

CREATE TABLE IF NOT EXISTS tags (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT      NOT NULL,
    name    VARCHAR(30) NOT NULL,
    CONSTRAINT fk_tags_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_tags_user_name UNIQUE (user_id, name)
);

CREATE TABLE IF NOT EXISTS task_tags (
    task_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (task_id, tag_id),
    CONSTRAINT fk_task_tags_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_tags_tag  FOREIGN KEY (tag_id)  REFERENCES tags(id)  ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_task_tags_tag ON task_tags(tag_id);

-- History deliberately has NO foreign key to tasks, so entries survive when a task is deleted.
CREATE TABLE IF NOT EXISTS task_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    task_id     BIGINT        NULL,
    task_title  VARCHAR(200)  NULL,
    event_type  VARCHAR(30)   NOT NULL,
    description VARCHAR(500)  NOT NULL,
    created_at  TIMESTAMP     NOT NULL,
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_history_user_time ON task_history(user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_history_task      ON task_history(user_id, task_id);
