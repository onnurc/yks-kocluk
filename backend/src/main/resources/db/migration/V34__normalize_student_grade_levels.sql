UPDATE student_profiles
SET grade_level = CASE
    WHEN lower(trim(grade_level)) IN ('9', '9.sınıf', '9. sınıf') THEN '9. Sınıf'
    WHEN lower(trim(grade_level)) IN ('10', '10.sınıf', '10. sınıf') THEN '10. Sınıf'
    WHEN lower(trim(grade_level)) IN ('11', '11.sınıf', '11. sınıf') THEN '11. Sınıf'
    WHEN lower(trim(grade_level)) IN ('12', '12.sınıf', '12. sınıf') THEN '12. Sınıf'
    ELSE grade_level
END
WHERE grade_level IS NOT NULL;
