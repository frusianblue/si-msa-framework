package com.company.user.controller;

import com.company.framework.core.page.PageRequest;
import com.company.framework.core.page.PageResponse;
import com.company.framework.core.response.ApiResponse;
import com.company.user.dto.PasswordChangeRequest;
import com.company.user.dto.UserCreateRequest;
import com.company.user.dto.UserResponse;
import com.company.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest req) {
        return ApiResponse.ok(userService.create(req), "사용자가 생성되었습니다.");
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeRequest req) {
        userService.changePassword(id, req);
        return ApiResponse.ok(null, "비밀번호가 변경되었습니다.");
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(userService.get(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<UserResponse>> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(userService.list(PageRequest.of(page, size)));
    }
}
