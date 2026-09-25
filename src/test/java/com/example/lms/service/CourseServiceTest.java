package com.example.lms.service;

import java.util.Optional;
import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private UserRepository userRepository;

    private CourseService courseService;
    private AuthPrincipal admin;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(courseRepository, courseEnrollmentRepository, userRepository);
        admin = new AuthPrincipal(1L, "admin@example.com", "ADMIN");
        User instructor = User.builder()
                .id(1L)
                .firstName("Demo")
                .lastName("Instructor")
                .email("instructor@example.com")
                .role("INSTRUCTOR")
                .active(true)
                .build();
        when(userRepository.findByIdAndRoleIgnoreCaseAndActiveTrue(1L, "INSTRUCTOR"))
                .thenReturn(Optional.of(instructor));
        when(courseRepository.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(courseRepository.save(any(CourseEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createCourseAcceptsNamesOutsideTheFormerThreeToTenWordRange() {
        assertEquals("Java", createCourse("Java").getName());
        assertEquals(
                "one two three four five six seven eight nine ten eleven",
                createCourse("one two three four five six seven eight nine ten eleven").getName());
    }

    private CourseDto.CourseResponse createCourse(String name) {
        CourseDto.CourseRequest request = CourseDto.CourseRequest.builder()
                .name(name)
                .category("Technical")
                .instructorId(1L)
                .courseLevel("Beginner")
                .build();

        return courseService.createCourse(request, admin);
    }
}
