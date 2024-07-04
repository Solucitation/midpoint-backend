package com.solucitation.midpoint_backend.domain.member.api;

import com.solucitation.midpoint_backend.domain.member.dto.EmailMessage;
import com.solucitation.midpoint_backend.domain.member.dto.PwResetRequestDto;
import com.solucitation.midpoint_backend.domain.member.dto.VerificationCodeRequestDto;
import com.solucitation.midpoint_backend.domain.member.dto.VerificationEmailRequestDto;
import com.solucitation.midpoint_backend.domain.member.service.EmailServiceV2;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class EmailControllerV2 {
    private final EmailServiceV2 emailServiceV2;
    private final MemberService memberService;

    // 유효한 이메일인지 판단하기 위해 인증코드 발송하는 api
    @PostMapping("/verify-email/v2")
    public ResponseEntity<String> sendVerificationEmail(@RequestBody VerificationEmailRequestDto verificationEmailRequestDto) {
        // 이메일 중복 체크
        if (memberService.isEmailAlreadyInUse(verificationEmailRequestDto.getEmail())) {
            return ResponseEntity.badRequest().body("이미 사용중인 이메일입니다.");
        }
        EmailMessage emailMessage = EmailMessage.builder()
                .to(verificationEmailRequestDto.getEmail())
                .subject("[midpoint] 이메일 인증을 위한 인증 코드 발송")
                .build();
        String code = emailServiceV2.sendVerificationMail(emailMessage, "verify");
        return ResponseEntity.ok(code);
    }

    // 비밀번호를 잊어버렸을 때 임시 비밀번호를 발급하는 api
    @PostMapping("/reset-pw/v2")
    public ResponseEntity<String> sendPwResetEmail(@RequestBody PwResetRequestDto pwResetRequestDto) {
        // 이름과 이메일이 모두 일치하는지 검증
        if (!memberService.isNameAndEmailMatching(pwResetRequestDto.getName(), pwResetRequestDto.getEmail())) {
            return ResponseEntity.badRequest().body("이름 또는 이메일 정보가 일치하지 않습니다.");
        }
        EmailMessage emailMessage = EmailMessage.builder()
                .to(pwResetRequestDto.getEmail())
                .subject("[midpoint] 비밀번호 초기화를 위한 인증 코드 발송")
                .build();
        String code = emailServiceV2.sendVerificationMail(emailMessage, "reset-pw");
        return ResponseEntity.ok(code);
    }

    // 사용자가 입력한 이메일 인증 코드를 검증하는 api
    @PostMapping("/verify-code/v2")
    public ResponseEntity<String> verifyCodeV2(@RequestBody VerificationCodeRequestDto verificationCodeRequestDto) {
        boolean isValid = emailServiceV2.verifyCode(verificationCodeRequestDto.getEmail(), verificationCodeRequestDto.getCode());

        if (isValid) {
            return ResponseEntity.ok("올바른 인증번호입니다.");
        } else {
            return ResponseEntity.badRequest().body("유효하지 않거나 만료된 인증번호입니다.");
        }
    }
    // TODO db의 member테이블의 member_pw 필드를 임시비밀번호로 update 필요
    // TODO 이때, 암호화 필수, 임시비밀번호 타이머 구현 (넉넉하게 10분)
}
