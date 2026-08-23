alter table student_profiles
    add column exam_year integer,
    add column yks_score_type varchar(20),
    add column exam_session varchar(10),
    add column target_university varchar(200),
    add column target_department varchar(150);

alter table student_profiles
    add constraint chk_student_profiles_exam_year
        check (exam_year is null or exam_year between 2024 and 2100),
    add constraint chk_student_profiles_yks_score_type
        check (yks_score_type is null or yks_score_type in ('EQUAL_WEIGHT', 'NUMERICAL', 'VERBAL', 'LANGUAGE')),
    add constraint chk_student_profiles_exam_session
        check (exam_session is null or exam_session in ('TYT', 'AYT', 'YDT'));
