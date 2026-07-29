package com.example.lms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public class SectionDto {

    // Add Section / Edit Section body
    public static class SectionRequest {

        @NotBlank(message = "Section Name is required.")
        private String name;

        public SectionRequest() {
        }

        public SectionRequest(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // Section List / Preview response
    public static class SectionResponse {
        private Long id;
        private Long courseId;
        private String name;
        private Integer sectionOrder;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public SectionResponse() {
        }

        private SectionResponse(Builder b) {
            this.id = b.id;
            this.courseId = b.courseId;
            this.name = b.name;
            this.sectionOrder = b.sectionOrder;
            this.createdAt = b.createdAt;
            this.updatedAt = b.updatedAt;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getCourseId() {
            return courseId;
        }

        public void setCourseId(Long courseId) {
            this.courseId = courseId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSectionOrder() {
            return sectionOrder;
        }

        public void setSectionOrder(Integer sectionOrder) {
            this.sectionOrder = sectionOrder;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long id;
            private Long courseId;
            private String name;
            private Integer sectionOrder;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;

            public Builder id(Long id) {
                this.id = id;
                return this;
            }

            public Builder courseId(Long courseId) {
                this.courseId = courseId;
                return this;
            }

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder sectionOrder(Integer sectionOrder) {
                this.sectionOrder = sectionOrder;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Builder updatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
                return this;
            }

            public SectionResponse build() {
                return new SectionResponse(this);
            }
        }
    }

    // Reorder body
    public static class ReorderRequest {

        @NotEmpty(message = "Reorder items list cannot be empty.")
        @Valid
        private List<ReorderItem> items;

        public ReorderRequest() {
        }

        public ReorderRequest(List<ReorderItem> items) {
            this.items = items;
        }

        public List<ReorderItem> getItems() {
            return items;
        }

        public void setItems(List<ReorderItem> items) {
            this.items = items;
        }
    }

    public static class ReorderItem {

        @NotNull(message = "sectionId is required.")
        private Long sectionId;

        @NotNull(message = "newOrder is required.")
        @Min(value = 1, message = "newOrder must be 1 or greater.")
        private Integer newOrder;

        public ReorderItem() {
        }

        public ReorderItem(Long sectionId, Integer newOrder) {
            this.sectionId = sectionId;
            this.newOrder = newOrder;
        }

        public Long getSectionId() {
            return sectionId;
        }

        public void setSectionId(Long sectionId) {
            this.sectionId = sectionId;
        }

        public Integer getNewOrder() {
            return newOrder;
        }

        public void setNewOrder(Integer newOrder) {
            this.newOrder = newOrder;
        }
    }

    // Generic wrapper for all API responses (Course + Section controllers both use this)
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public ApiResponse() {
        }

        private ApiResponse(Builder<T> b) {
            this.success = b.success;
            this.message = b.message;
            this.data = b.data;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }

        public static <T> ApiResponse<T> success(String message, T data) {
            return new Builder<T>().success(true).message(message).data(data).build();
        }

        public static <T> ApiResponse<T> success(String message) {
            return new Builder<T>().success(true).message(message).build();
        }

        public static <T> Builder<T> builder() {
            return new Builder<>();
        }

        public static class Builder<T> {
            private boolean success;
            private String message;
            private T data;

            public Builder<T> success(boolean success) {
                this.success = success;
                return this;
            }

            public Builder<T> message(String message) {
                this.message = message;
                return this;
            }

            public Builder<T> data(T data) {
                this.data = data;
                return this;
            }

            public ApiResponse<T> build() {
                return new ApiResponse<>(this);
            }
        }
    }
}
