package com.solucitation.midpoint_backend.domain.community_board.service;

import com.solucitation.midpoint_backend.domain.community_board.dto.*;
import com.solucitation.midpoint_backend.domain.community_board.entity.*;
import com.solucitation.midpoint_backend.domain.community_board.repository.*;
import com.solucitation.midpoint_backend.domain.file.service.S3Service;
import com.solucitation.midpoint_backend.domain.member.dto.MemberProfileResponseDto;
import com.solucitation.midpoint_backend.domain.member.entity.Member;
import com.solucitation.midpoint_backend.domain.member.service.MemberService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final LikesRepository likesRepository;
    private final HashtagRepository hashtagRepository;
    private final S3Service s3Service;
    private final ImageRepository imageRepository;
    private final MemberService memberService;
    private final PostHashtagRepository postHashtagsRepository;

    /**
     * 특정 게시글을 상세조회 합니다.
     *
     * @param postId 게시글 번호
     * @param read_member 게시글을 보는 사람 정보
     * @return 게시글 상세정보 데이터
     */
    @Transactional(readOnly = true)
    public PostDetailDto getPostById(Long postId, Member read_member) {
        Post post = postRepository.findById(postId).orElseThrow(EntityNotFoundException::new);
        String memberEmail = post.getMember().getEmail();

        MemberProfileResponseDto memberProfileResponseDto = memberService.getMemberProfile(memberEmail);

        List<String> images = post.getImages().stream() // 이미지가 순서대로 나오게 합니다.
                .sorted(Comparator.comparingInt(Image::getOrder)) // order 값에 따라 정렬
                .map(Image::getImageUrl) // 이미지 URL 추출
                .toList(); // 리스트로 변환

        List<Long> hashtags = post.getPostHashtags().stream()
                .map(postHashtag -> postHashtag.getHashtag().getId())
                .toList();

        int likeCnt = likesRepository.countByPostIdAndIsLiked(postId);

        boolean likes = false;  // 게시글을 보는 사람이 로그인하지 않은 상태라면 좋아요는 누르지 않은 상태로 반환합니다.
        if (read_member != null) { // 게시글을 보는 사람이 로그인된 상태라면 좋아요 여부를 확인해 반환합니다.
            likes = likesRepository.isMemberLikesPostByEmail(post.getId(), read_member.getEmail());
        }

        return new PostDetailDto(
                memberProfileResponseDto.getNickname(),
                memberProfileResponseDto.getProfileImageUrl(),
                post.getTitle(),
                post.getContent(),
                post.getCreateDate(),
                hashtags,
                images,
                likeCnt,
                likes
        );
    }

    /**
     * 해당 게시글이 존재하는지 확인합니다.
     *
     * @param postId 게시글 번호
     * @return 게시글의 존재 여부에 따라 true or false를 반환합니다.
     */
    @Transactional(readOnly = true)
    public Boolean isPostExist(Long postId){
        return postRepository.existsById(postId);
    }

    /**
     * 모든 게시글을 가져옵니다.
     *
     * @param member 게시글을 보는 사람 정보
     * @return 게시글 리스트에 보일 게시글 정보 리스트
     */
    @Transactional(readOnly = true)
    public List<PostResponseDto> getAllPosts(Member member) {
        List<Post> posts = postRepository.findAllPostWithImagesAndPostHashtags();

        return posts.stream()
                .map(post -> {
                    PostResponseDto postDto = new PostResponseDto(post); // postDto의 likes는 false로 초기화되어 있습니다.
                    if (member != null) { // 로그인된 사용자에 대해 사용자가 해당 게시글에 좋아요를 눌렀는지 확인합니다.
                        postDto.setLikes(likesRepository.isMemberLikesPostByEmail(post.getId(), member.getEmail()));
                    }

                    return postDto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 좋아요를 누르거나 취소합니다
     *
     * @param memberEmail 로그인된 사용자 정보
     * @param postId 게시글 번호
     * @return 최종적으로 변화된 상태를 true or false로 반환합니다.
     */
    @Transactional
    public Boolean changeLikes(String memberEmail,  Long postId) {
        boolean existingLike = likesRepository.isMemberLikesPostByEmail(postId, memberEmail);

        if (existingLike) { // 좋아요를 누른 상태라면 DB에서 해당 데이터를 삭제하여 좋아요를 취소합니다.
            likesRepository.deleteByMemberEmailAndPostId(memberEmail, postId);
            return false;
        } else { // 좋아요를 누르지 않은 상태라면 DB에 해당 데이터를 추가하여 좋아요를 표시합니다.
            Member member = memberService.getMemberByEmail(memberEmail);
            Optional<Post> post = postRepository.findById(postId);
            if (member != null && post.isPresent()) {
                Likes likes = new Likes(post.get(), member);
                likesRepository.save(likes);
                return true;
            }
        }
        throw new IllegalArgumentException("좋아요 상태를 변경하는 중 오류가 발생하였습니다.");
    }

    /**
     * 게시글을 생성합니다.
     * 
     * @param postRequestDto 게시글 데이터 (제목, 본문, 해시태그)
     * @param member 사용자
     * @param postImages 업로드한 이미지
     */
    @Transactional
    public void createPost(PostRequestDto postRequestDto, Member member, List<MultipartFile> postImages) {
        postRequestDto.validatePostHashtags(); // 해시태그 검증 

        List<PostHashtag> postHashtags = new ArrayList<>(); // 원활한 연관관계 설정을 위해 미리 빈 리스트 생성
        List<Image> images = new ArrayList<>(); // 원활한 연관관계 설정을 위해 미리 빈 리스트 생성

        Post post = Post.builder()
                .member(member)
                .title(postRequestDto.getTitle())
                .content(postRequestDto.getContent())
                .postHashtags(postHashtags)
                .images(images)
                .build();

        postRepository.save(post); 

        postHashtags = addHashtags(post, postRequestDto.getPostHashtag()); // 해시태그 정보 저장
        images = addImages(post, member, postImages); // 이미지 정보 저장

        try {
            log.info("게시글 등록 성공");
        } catch (Exception e) {
            throw new RuntimeException("게시글 등록 실패");
        }
    }

    /**
     * 게시글 생성 시 이미지를 확인하고 저장합니다.
     * 
     * @param post 게시글
     * @param member 사용자
     * @param postImages 저장할 이미지 파일 리스트
     * @return 성공적으로 저장된 이미지 리스트
     */
    @Transactional
    public List<Image> addImages(Post post, Member member,  List<MultipartFile> postImages) {
        List<Image> images = new ArrayList<>(); // 게시글에 추가된 이미지를 저장할 리스트
        if (!postImages.isEmpty()) {
            try {
                int cnt = 1; // 이미지 순서를 지정해 이미지를 추가한 순서대로 옯바르게 보일 수 있도록 합니다.
                for (MultipartFile postImage : postImages) {
                    if (postImage.isEmpty()) {
                        log.error("게시글 이미지 업로드 실패");
                        throw new RuntimeException("이미지가 존재하지 않습니다.");
                    }
                    String postImageUrl = s3Service.upload("post-images", postImage.getOriginalFilename(), postImage);

                    Image image = Image.builder()
                            .imageUrl(postImageUrl).member(member).post(post).order(cnt).build();
                    imageRepository.save(image);
                    images.add(image);
                    cnt++;
                }
            } catch (IOException e){
                log.error("게시글 이미지 업로드 실패: {}", e.getMessage());
                throw new RuntimeException("게시글 이미지 업로드에 실패하였습니다.");
            }
        }
        return images;
    }

    /**
     * 게시글 생성 및 수정 시 해시태그를 확인하고 저장합니다.
     * 
     * @param post 게시글
     * @param hashtags 수정될 해시태그 리스트
     * @return 성공적으로 추가/변경될 수 있는 해시태그 리스트 
     */
    @Transactional
    public List<PostHashtag> addHashtags(Post post, List<Long> hashtags) {
        List<PostHashtag> postHashtags = new ArrayList<>();
        for (Long tagId : hashtags) {
            Hashtag hashtag = hashtagRepository.findById(tagId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 해시태그는 존재하지 않습니다."));
            PostHashtag postHashtag = new PostHashtag(post, hashtag);
            postHashtagsRepository.save(postHashtag);
            postHashtags.add(postHashtag);
        }
        return postHashtags;
    }

    /**
     * 사용자가 작성한 게시글을 전부 최신순부터 반환합니다.
     * 
     * @param member 사용자
     * @return 게시글 리스트에 보일 게시글 데이터 리스트
     */
    @Transactional(readOnly = true)
    public List<PostResponseDto> getMyAllPosts(Member member) {
        List<Post> posts = postRepository.findByMemberIdOrderByCreateDateDesc(member.getId());
        return posts.stream()
                .map(post -> {
                    PostResponseDto postDto = new PostResponseDto(post);
                    if (member != null) { // 일반적인 접근에서는 NULL 이 될 수 없지만, 기타 경로로 호출될 경우를 고려합니다.
                        postDto.setLikes(likesRepository.isMemberLikesPostByEmail(post.getId(), member.getEmail()));
                    }
                    return postDto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 게시글을 삭제합니다.
     *
     * @param member 사용자
     * @param postId 게시글 번호
     * @throws AccessDeniedException 타인의 글을 삭제하려고 할 떄 권한 에러를 반환합니다.
     */
    @Transactional
    public void deletePost(Member member, Long postId) throws AccessDeniedException {
        Optional<Post> post = postRepository.findById(postId); // 해당 멤버가 게시글 작성자인지 확인힙니다.
        if (post.isPresent() && post.get().getMember().getId().equals(member.getId())) {
            likesRepository.deleteByPostId(postId);
            deleteImages(post.get().getImages()); // s3에 저장된 이미지 삭제
            postRepository.deleteById(postId);
        }
        else
            throw new AccessDeniedException("해당 게시글을 삭제할 권한이 없습니다. 본인이 작성한 글만 삭제할 수 있습니다.");
    }

    /**
     * 해시태그를 이용하여 게시글을 검색하고 결과를 반환합니다.
     * 
     * @param member 사용자
     * @param purposes 해시태그 리스트
     * @return 게시글 리스트에 보일 게시글 데이터 리스트
     */
    @Transactional(readOnly = true)
    public List<PostResponseDto> getPostByPurpose(Member member, List<Long> purposes) {
        if (purposes.isEmpty()) {
            throw new IllegalArgumentException("최소 하나 이상의 해시태그를 선택해야 합니다.");
        }
        for (Long tagId : purposes) { // 해시태그 유효성 검사
            Hashtag hashtag = hashtagRepository.findById(tagId)
                    .orElseThrow(() -> new IllegalArgumentException("해당 해시태그는 존재하지 않습니다."));
        }
        List<Post> posts = postRepository.findAllPostByPurpose(purposes);

        return posts.stream()
                .map(post -> {
                    PostResponseDto postDto = new PostResponseDto(post);
                    if (member != null) {
                        postDto.setLikes(likesRepository.isMemberLikesPostByEmail(post.getId(), member.getEmail()));
                    }
                    return postDto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 검색어를 이용한 게시글 검색 결과를 반환합니다.
     * @param member 사용자
     * @param query 검색어 (문장)
     * @return 게시글 리스트에 보일 게시글 데이터 리스트
     */
    @Transactional(readOnly = true)
    public List<PostResponseDto> getPostByQuery(Member member, String query) {
        if (query.isEmpty() || query.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }
        String[] words = query.split("\\s+"); // 문자열일 경우 공백을 기준으로 파싱합니다.

        Set<Post> resultSet = wordsToPosts(words);

        // Set을 List로 변환하고 postId를 기준으로 내림차순 정렬합니다.
        List<Post> sortedPosts = resultSet.stream()
                .sorted(Comparator.comparing(Post::getId).reversed())
                .collect(Collectors.toList());

        List<PostResponseDto> postResponseDtos = sortedPosts.stream()
                .map(post -> {
                    PostResponseDto postDto = new PostResponseDto(post);
                    if (member != null) {
                        postDto.setLikes(likesRepository.isMemberLikesPostByEmail(post.getId(), member.getEmail()));
                    }
                    return postDto;
                }).collect(Collectors.toList());

        return postResponseDtos;
    }

    /**
     * 검색어를 이용해 검색한 결과를 반환합니다.
     * 
     * @param words 단어 리스트
     * @return 검색 결과로 조회된 게시글 리스트
     */
    private Set<Post> wordsToPosts(String[] words) {
        Set<Post> resultSet = new LinkedHashSet<>(); // 게시글 최신순 순서 보장을 위해 LinkedHashSet 사용

        for (String word : words) {
            List<Post> posts = postRepository.findAllPostByQuery(word);
            resultSet.addAll(posts);
        }

        return resultSet;
    }

    /**
     * 게시글을 수정합니다.
     * 
     * @param postId 게시글 번호
     * @param postUpdateDto 수정할 게시글 데이터 (제목, 본문, 해시태그, 삭제할 이미지 url)
     * @param member 사용자
     * @param postImages 추가할 이미지 파일 리스트
     * @throws AccessDeniedException 타인의 글을 수정하려 시도 시 권한 에러를 반환합니다.
     * 해당 함수는 다음과 같이 작동합니다.
     * 1. 해시태그 변경 시도
     * 해시태그 리스트가 유효한 데이터로 들어올 경우에는 최종적으로 선택된 해시태그 2개의 정보가 포함되어 있어야 합니다.
     * 해시태그 검증 수행 후 해시태그를 업데이트합니다.
     * 2. 이미지 변경 시도 [이미지 개수 조건은 컨트롤러 계층에서 미리 처리]
     * 이미지가 변경되는 경우에는 이미지 추가 혹은 삭제가 이루어집니다 (추가와 삭제가 모두 발생할 수도 있습니다.)
     * 2.1 먼저 삭제할 이미지 url 리스트에 비어 있지 않을 경우 삭제를 시도합니다.
     * 이를 위해 이미지 DB 에서 url 과 게시글 아이디를 이용해 조회하고 결과가 유효할 경우 이미지 DB와 S3에서 이미지를 삭제합니다.
     * 삭제가 완료되면 기존 이미지 리스트에서도 자동으로 삭제됩니다.
     * 2.2. 추가할 이미지가 존재할 경우
     * 게시글 수정용 이미지 추가 함수를 호출해 이미지를 저장합니다.
     * 성공적으로 이미지가 저장되었을 경우 기존 이미지 리스트에 추가합니다.
     * 3. 최종적인 변경 사항을 POST 에 대입힙니다.
     */
    @Transactional
    public void updatePost(Long postId, PostUpdateDto postUpdateDto, Member member, List<MultipartFile> postImages)
            throws AccessDeniedException {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("해당 게시글이 존재하지 않습니다."));

        if (!post.getMember().getId().equals(member.getId())) {
            throw new AccessDeniedException("해당 게시글을 수정할 권한이 없습니다. 본인이 작성한 글만 수정할 수 있습니다.");
        }

        List<PostHashtag> newPostHashtag = null;
        if (postUpdateDto.getPostHashtag() != null)  // 해시태그 변경 시 수정
            newPostHashtag = addHashtags(post, postUpdateDto.getPostHashtag());

        List<Image> existImages = new ArrayList<>(post.getImages());
        updateImageOrders(existImages);
        List<Image> newImages = null;

        try {
            List<Integer> deleteIdx = new ArrayList<>();
            // 기존 이미지 삭제
            if (postUpdateDto.getDeleteImageUrl() != null && !postUpdateDto.getDeleteImageUrl().isEmpty()) {
                for (String imageUrl : postUpdateDto.getDeleteImageUrl()) {
                    Long cnt = imageRepository.countByImageUrlAndPostId(imageUrl, postId);
                    if (cnt == 0) {
                        throw new RuntimeException("해당 게시글에 존재하지 않는 이미지는 삭제할 수 없습니다.");
                    }
                }
                deleteIdx = deleteImagesWithUrl(postUpdateDto.getDeleteImageUrl(), postId);
            }

            if (postImages != null && !postImages.isEmpty()) {
                newImages = addForUpdateImages(post, member, postImages, deleteIdx);
                if (newImages != null && !newImages.isEmpty()) {
                    existImages.addAll(newImages);
                }
            }

            post = Post.builder()
                    .id(post.getId())
                    .member(post.getMember())
                    .title(postUpdateDto.getTitle() != null ? postUpdateDto.getTitle() : post.getTitle())
                    .content(postUpdateDto.getContent() != null ? postUpdateDto.getContent() : post.getContent())
                    .postHashtags(newPostHashtag != null && !newPostHashtag.isEmpty() ? newPostHashtag : post.getPostHashtags())
                    .images(existImages)
                    .build();

            postRepository.save(post);
        } catch (Exception e) {
            // 트랜잭션 롤백
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw e; // 오류를 다시 던집니다.
        }
    }

    /**
     * 게시글 수정 시에만 사용되는 이미지 추가 함수
     * 
     * @param post 게시글
     * @param member 사용자
     * @param postImages 추가할 이미지
     * @param deleteId 추가할 이미지가 대입될 이미지 순서 리스트
     * @return 추가된 이미지 리스트
     */
    @Transactional
    protected List<Image> addForUpdateImages(Post post, Member member, List<MultipartFile> postImages, List<Integer> deleteId) {
        List<Image> images = new ArrayList<>();

        int idx = 0;

        if (!postImages.isEmpty()) {
            try {
                for (MultipartFile postImage : postImages) {
                    if (postImage.isEmpty())  // 이미지가 유효하지 않음.
                        continue;

                    String postImageUrl = s3Service.upload("post-images", postImage.getOriginalFilename(), postImage);

                    int order;
                    if (!deleteId.isEmpty() && idx < deleteId.size()) {
                        order = deleteId.get(idx);
                    } else {
                        order = post.getImages().size() + idx + 1; // 기존 이미지 개수 + 현재 추가된 이미지 순서
                    }

                    Image image = Image.builder()
                            .imageUrl(postImageUrl).member(member).post(post)
                            .order(order)
                            .build();

                    imageRepository.save(image);
                    images.add(image);
                    idx++;
                }
            } catch (IOException e) {
                log.error("게시글 이미지 업로드 실패: {}", e.getMessage());
                throw new RuntimeException("게시글 이미지 업로드에 실패하였습니다.");
            }
        }
        return images;
    }

    /**
     * 게시글 수정 시 최종적으로 이미지 순서를 정리하는 함수
     * 이를 통해 이미지 순서는 1~3 사이임을 보장합니다.
     * 
     * @param images 이미지 리스트
     */
    @Transactional
    protected void updateImageOrders(List<Image> images) {
        // 이미지 리스트를 order 값에 따라 오름차순으로 정렬
        images.sort(Comparator.comparingInt(image -> image.getOrder() != null ? image.getOrder() : Integer.MAX_VALUE));

        int order = 1;
        for (Image image : images) {
            image.setOrder(order++);
        }
        imageRepository.saveAll(images); // 이미지의 order 값을 데이터베이스에 반영
    }

    /**
     * 게시글 삭제 시 해당 게시글에 존재하는 모든 이미지를  DB 와 S3 에서 모두 삭제합니다.
     * @param images 삭제할 이미지 리스트
     */
    @Transactional
    protected void deleteImages(List<Image> images) {
        for (Image image : images) {
            s3Service.delete(image.getImageUrl()); // S3에서 이미지 삭제
        }
        imageRepository.deleteAll(images);
    }

    /**
     * 게시글 수정 시 삭제할 이미지를 처리하는 함수
     * 
     * @param images 삭제할 이미지의 URL 리스트
     * @param postId 게시글 번호
     * @return 삭제된 이미지들의 순서 리스트
     */
    @Transactional
    protected List<Integer> deleteImagesWithUrl(List<String> images, Long postId) {
        List<Integer> deleteImageIdx = new ArrayList<>();
        for (String imageUrl : images) {
            Integer idx = imageRepository.findOrderByImageUrlAndPostId(imageUrl, postId);
            if (idx != null) {
                s3Service.delete(imageUrl);
                imageRepository.deleteImageByImageUrlAndPostId(imageUrl, postId);
                deleteImageIdx.add(idx);
            }
        }
        return deleteImageIdx;
    }
}
