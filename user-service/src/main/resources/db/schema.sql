create table if not exists user_account (
    id bigint primary key auto_increment,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    status varchar(32) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists user_profile (
    id bigint primary key auto_increment,
    user_id bigint not null,
    nickname varchar(128) not null,
    avatar_url varchar(512),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    constraint uk_user_profile_user_id unique (user_id)
);

create table if not exists user_address (
    address_id bigint primary key auto_increment,
    owner_user_id bigint not null,
    recipient_name varchar(64) not null,
    recipient_phone varchar(32) not null,
    province varchar(64) not null,
    city varchar(64) not null,
    district varchar(64) not null,
    detail_address varchar(255) not null,
    postal_code varchar(32),
    default_address tinyint(1) not null default 0,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp,
    deleted_at timestamp null
);

create table if not exists user_session (
    id bigint primary key auto_increment,
    user_id bigint not null,
    status varchar(32) not null,
    source_ip varchar(64),
    user_agent varchar(512),
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists user_refresh_token (
    id bigint primary key auto_increment,
    session_id bigint not null,
    token_hash varchar(255) not null unique,
    status varchar(32) not null,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists verification_code (
    id bigint primary key auto_increment,
    purpose varchar(32) not null,
    target varchar(255) not null,
    code_hash varchar(255) not null,
    status varchar(32) not null,
    attempt_count int not null default 0,
    max_attempts int not null,
    expires_at timestamp not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp on update current_timestamp
);

create table if not exists security_event (
    id bigint primary key auto_increment,
    event_type varchar(64) not null,
    user_id bigint,
    session_id bigint,
    source_ip varchar(64),
    detail text,
    created_at timestamp not null default current_timestamp
);
