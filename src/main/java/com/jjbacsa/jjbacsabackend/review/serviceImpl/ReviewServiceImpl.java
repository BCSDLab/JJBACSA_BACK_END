package com.jjbacsa.jjbacsabackend.review.serviceImpl;

import com.jjbacsa.jjbacsabackend.etc.enums.ErrorMessage;
import com.jjbacsa.jjbacsabackend.etc.exception.RequestInputException;
import com.jjbacsa.jjbacsabackend.follow.repository.FollowRepository;
import com.jjbacsa.jjbacsabackend.image.service.ImageService;
import com.jjbacsa.jjbacsabackend.review.dto.request.ReviewRequest;
import com.jjbacsa.jjbacsabackend.review.dto.response.ReviewDeleteResponse;
import com.jjbacsa.jjbacsabackend.review.dto.response.ReviewResponse;
import com.jjbacsa.jjbacsabackend.review.entity.ReviewEntity;
import com.jjbacsa.jjbacsabackend.review.mapper.ReviewMapper;
import com.jjbacsa.jjbacsabackend.review.repository.ReviewRepository;
import com.jjbacsa.jjbacsabackend.review.service.ReviewService;
import com.jjbacsa.jjbacsabackend.review_image.entity.ReviewImageEntity;
import com.jjbacsa.jjbacsabackend.review_image.repository.ReviewImageRepository;
import com.jjbacsa.jjbacsabackend.shop.entity.ShopEntity;
import com.jjbacsa.jjbacsabackend.shop.repository.ShopRepository;
import com.jjbacsa.jjbacsabackend.user.entity.UserEntity;
import com.jjbacsa.jjbacsabackend.user.repository.UserRepository;
import com.jjbacsa.jjbacsabackend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.util.List;


@Slf4j
@RequiredArgsConstructor
@Service
public class ReviewServiceImpl implements ReviewService {
    private final UserService userService;
    private final ImageService imageService;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewRepository reviewRepository;
    private final FollowRepository followRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(ReviewRequest reviewRequest) throws Exception {
        ReviewEntity review = reviewRepository.save(createReviewEntity(reviewRequest));
        return ReviewResponse.from(review);
    }

    @Override
    @Transactional
    public ReviewResponse modifyReview(ReviewRequest reviewRequest, Long reviewId) throws Exception {
        UserEntity userEntity = verifyUser();
        ReviewEntity review = reviewRepository.findByReviewId(reviewId);
        if(review == null) throw new RequestInputException(ErrorMessage.REVIEW_NOT_EXISTS_EXCEPTION);
        if(!review.getWriter().equals(userEntity)) throw new RequestInputException(ErrorMessage.INVALID_PERMISSION_REVIEW);
        if(reviewRequest.getContent() != null) review.setContent(reviewRequest.getContent());  // not null 컬럼
        modifyReviewInfo(review, reviewRequest);

        return ReviewResponse.from(review);
    }

    @Override
    @Transactional
    public ReviewDeleteResponse deleteReview(Long reviewId) throws Exception {
        UserEntity userEntity = verifyUser();
        ReviewEntity reviewEntity = reviewRepository.findByReviewId(reviewId);
        if(reviewEntity == null) throw new RequestInputException(ErrorMessage.REVIEW_NOT_EXISTS_EXCEPTION);
        if(!reviewEntity.getWriter().equals(userEntity)) throw new RequestInputException(ErrorMessage.INVALID_PERMISSION_REVIEW);
        reviewRepository.deleteById(reviewId);

        // 리뷰 수, 별점 처리
        userEntity.getUserCount().decreaseReviewCount();
        reviewEntity.getShop().getShopCount().decreaseTotalRating(reviewEntity.getRate());
        reviewEntity.getShop().getShopCount().decreaseRatingCount();

        return ReviewDeleteResponse.from(reviewEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewResponse getReview(Long reviewId) {
        ReviewEntity review = reviewRepository.findByReviewId(reviewId);
        if(review == null) throw new RequestInputException(ErrorMessage.REVIEW_NOT_EXISTS_EXCEPTION);
        return ReviewResponse.from(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> searchShopReviews(Long shopId, Integer page, Integer size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return reviewRepository.findAllByShopId(shopId, pageRequest).map(ReviewMapper.INSTANCE::fromReviewEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> searchWriterReviews(Long writerId,  Integer page, Integer size){
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return reviewRepository.findAllByWriterId(writerId, pageRequest).map(ReviewMapper.INSTANCE::fromReviewEntity);
    }
    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(Integer page, Integer size) throws Exception {
        UserEntity user = verifyUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return reviewRepository.findAllByWriterId(user.getId(), pageRequest).map(ReviewMapper.INSTANCE::fromReviewEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getFollowersReviews(Integer page, Integer size) throws Exception {
        UserEntity user = verifyUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return reviewRepository.findAllFriendsReview(user.getId(), pageRequest).map(ReviewResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> searchFollowerReviews(String followerAccount, Integer page, Integer size) throws Exception {
        UserEntity user = verifyUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        UserEntity follower = userRepository.findByAccount(followerAccount)
                .orElseThrow(() -> new RequestInputException(ErrorMessage.USER_NOT_EXISTS_EXCEPTION));
        if(followRepository.existsByUserAndFollower(user, follower)){
            return reviewRepository.findAllByFollowerId(follower.getId(), pageRequest).map(ReviewResponse::from);
        }
        else throw new RequestInputException(ErrorMessage.NOT_FOLLOWED_EXCEPTION);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReviewResponse> searchFollowersShopReviews(Long shopId, Integer page, Integer size) throws Exception {
        UserEntity user = verifyUser();
        PageRequest pageRequest = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        return reviewRepository.findAllFollowersReviewsByShopId(user.getId(), shopId, pageRequest).map(ReviewResponse::from);
    }

    private ReviewEntity createReviewEntity(ReviewRequest reviewRequest) throws Exception {
        UserEntity userEntity = verifyUser();
        ShopEntity shopEntity = verifyShop(reviewRequest.getShopId());
        ReviewEntity reviewEntity = ReviewEntity.builder()
                .writer(userEntity)
                .shop(shopEntity)
                .content(reviewRequest.getContent())
                .rate(reviewRequest.getRate())
                .build();

        if(reviewRequest.getReviewImages() != null) {
            List<ReviewImageEntity> reviewImageEntities = imageService.createReviewImages(reviewRequest.getReviewImages());
            for(ReviewImageEntity reviewImageEntity : reviewImageEntities){
                reviewEntity.addReviewImageEntity(reviewImageEntity);
            }
        }

        // 리뷰 수 증가
        userEntity.getUserCount().increaseReviewCount();
        // 상점 별점 증가
        shopEntity.getShopCount().increaseTotalRating(reviewRequest.getRate());
        shopEntity.getShopCount().increaseRatingCount();

        return reviewEntity;
    }

    private UserEntity verifyUser() throws Exception {
        return userRepository.findById(userService.getLoginUser().getId())
                .orElseThrow(() -> new RequestInputException(ErrorMessage.USER_NOT_EXISTS_EXCEPTION));
    }
    private ShopEntity verifyShop(Long shopId){
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new  RequestInputException(ErrorMessage.SHOP_NOT_EXISTS_EXCEPTION));
    }

    private void modifyReviewInfo(ReviewEntity review, ReviewRequest reviewRequest) throws IOException {
        Integer curRate = review.getRate();
        Integer modRate = reviewRequest.getRate();

        if(modRate != null) {
            review.getShop().getShopCount().decreaseTotalRating(curRate);
            review.getShop().getShopCount().increaseTotalRating(modRate);
            review.setRate(modRate);
        }

        if(reviewRequest.getReviewImages() != null) {
            imageService.modifyReviewImages(reviewRequest.getReviewImages(), review);
        }
        else{
            if(review.getReviewImages() != null){
                for(ReviewImageEntity image: review.getReviewImages()){
                    reviewImageRepository.deleteById(image.getId());
                }
                review.getReviewImages().clear();
            }
        }
    }
}
