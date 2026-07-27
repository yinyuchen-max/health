package com.health.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.health.domain.entity.DoctorAppointment;
import com.health.mapper.DoctorAppointmentMapper;
import com.health.service.DoctorAppointmentService;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DoctorAppointmentServiceImpl extends ServiceImpl<DoctorAppointmentMapper, DoctorAppointment>
        implements DoctorAppointmentService {

    private final JdbcTemplate jdbcTemplate;

    public DoctorAppointmentServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureAppointmentTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS doctor_appointment (
                    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NULL COMMENT 'system user id',
                    patient_name VARCHAR(50) NOT NULL COMMENT 'patient name',
                    age INT NOT NULL COMMENT 'age',
                    appointment_time DATETIME NOT NULL COMMENT 'appointment time',
                    phone VARCHAR(20) NOT NULL COMMENT 'phone',
                    department VARCHAR(50) NOT NULL COMMENT 'department',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_appointment_user_id (user_id),
                    INDEX idx_appointment_time (appointment_time),
                    INDEX idx_appointment_phone (phone),
                    CONSTRAINT doctor_appointment_ibfk_1
                        FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE SET NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='doctor appointment'
                """);
    }

    @Override
    public boolean createAppointment(DoctorAppointment appointment) {
        appointment.setCreatedAt(LocalDateTime.now());
        return save(appointment);
    }
}
