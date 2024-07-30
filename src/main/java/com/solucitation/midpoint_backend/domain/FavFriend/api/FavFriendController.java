package com.solucitation.midpoint_backend.domain.FavFriend.api;

import com.solucitation.midpoint_backend.domain.FavFriend.dto.FavFriendRequest;
import com.solucitation.midpoint_backend.domain.FavFriend.entity.FavFriend;
import com.solucitation.midpoint_backend.domain.FavFriend.service.FavFriendService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favs/friends")
@RequiredArgsConstructor
public class FavFriendController {
    private final FavFriendService favFriendService;

    @PostMapping("/save")
    public ResponseEntity<?> saveFavFriend(@Valid @RequestBody FavFriendRequest favFriendRequest, BindingResult result) {
        if (result.hasErrors()) {
            String errorMessage = result.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, "입력 값이 잘못되었습니다: " + errorMessage));
        }

        try {
            FavFriend savedFriend = favFriendService.saveFavoriteFriend(
                    favFriendRequest.getAddress(),
                    favFriendRequest.getName(),
                    favFriendRequest.getLatitude(),
                    favFriendRequest.getLongitude()
            );
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "즐겨찾는 친구 저장에 성공했습니다.", savedFriend.getFavFriendId()));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, "이미 존재하는 친구입니다."));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "서버 오류가 발생했습니다. " + e.getMessage()));
        }
    }

    @GetMapping("/details")
    public ResponseEntity<?> getFavFriendDetails(@RequestParam String name) {
        try {
            FavFriend favFriend = favFriendService.getFavoriteFriendByName(name);
            return ResponseEntity.ok(new FavFriendResponse(favFriend.getName(), favFriend.getAddress()));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "서버 오류가 발생했습니다. " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteFavFriend(@RequestParam String name) {
        try {
            favFriendService.deleteFavoriteFriendByName(name);
            return ResponseEntity.ok(new ApiResponse(true, "즐겨찾는 친구 삭제에 성공했습니다."));
        } catch (RuntimeException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ApiResponse(false, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "서버 오류가 발생했습니다. " + e.getMessage()));
        }
    }

    public static class ApiResponse {
        private boolean success;
        private String message;
        private Long favFriendId;

        public ApiResponse(boolean success, String message, Long favFriendId) {
            this.success = success;
            this.message = message;
            this.favFriendId = favFriendId;
        }

        public ApiResponse(boolean success, String message) {
            this(success, message, null);
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

        public Long getFavFriendId() {
            return favFriendId;
        }

        public void setFavFriendId(Long favFriendId) {
            this.favFriendId = favFriendId;
        }
    }

    @Getter
    @Setter
    public static class FavFriendResponse {
        private String name;
        private String address;

        public FavFriendResponse(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }
}