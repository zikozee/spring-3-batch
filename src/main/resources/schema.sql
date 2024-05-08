create table if not exists video_game_sales
(
    rank         int,
    name         text,
    platform     text,
    year         int,
    genre        text,
    publisher    text,
    na_sales     float,
    eu_sales     float,
    jp_sales     float,
    other_sales  float,
    global_sales float

)
