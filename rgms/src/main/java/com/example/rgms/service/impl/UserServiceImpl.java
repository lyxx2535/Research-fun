package com.example.rgms.service.impl;

import com.example.rgms.constant.MessageState;
import com.example.rgms.constant.MessageType;
import com.example.rgms.constant.UserType;
import com.example.rgms.entity.MessageEntity;
import com.example.rgms.entity.ResearchGroupEntity;
import com.example.rgms.entity.user.StudentEntity;
import com.example.rgms.entity.user.TeacherEntity;
import com.example.rgms.entity.user.UserEntity;
import com.example.rgms.exception.BadRequestException;
import com.example.rgms.exception.NotFoundException;
import com.example.rgms.exception.RgmsException;
import com.example.rgms.repository.user.UserRepository;
import com.example.rgms.service.*;
import com.example.rgms.utils.TokenUtil;
import com.example.rgms.vo.researchgroup.GroupInfoWithoutId;
import com.example.rgms.vo.user.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {
    @Value(value = "${user.default-portrait-url}")
    private String defaultPortraitUrl;
    @Value(value = "${research-group.direction-separator}")
    private String directionSep;

    private final UserRepository userRepository;
    private final StudentService studentService;
    private final TeacherService teacherService;
    private final ResearchGroupService researchGroupService;
    private final MessageService messageService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, StudentService studentService,
                           TeacherService teacherService, ResearchGroupService researchGroupService,
                           MessageService messageService, BCryptPasswordEncoder passwordEncoder){
        this.userRepository=userRepository;
        this.studentService=studentService;
        this.teacherService=teacherService;
        this.researchGroupService=researchGroupService;
        this.messageService=messageService;
        this.passwordEncoder=passwordEncoder;
    }

    @Override
    public LoginRet login(LoginForm loginForm) {
        String username=loginForm.getUsername(), password=loginForm.getPassword();

        if(!userRepository.existsByUsername(username))
            throw new BadRequestException("???????????????????????????");

        UserEntity userEntity=userRepository.findByUsername(username);
        if(!passwordEncoder.matches(password, userEntity.getPassword()))
            throw new BadRequestException("???????????????");

        Long userId=userEntity.getId();

        // ??????userType
        UserType userType;
        if(studentService.existsById(userId)){
            userType=UserType.STUDENT;
        } else if(teacherService.existsById(userId)){
            userType=UserType.TEACHER;
        } else {
            throw new RgmsException("id???"+userId+"??????????????????????????????????????????");
        }

        return LoginRet.builder()
                .userId(userEntity.getId())
                .userType(userType)
                .token(TokenUtil.sign(username))
                .build();
    }

    @Override
    public Boolean existsTheUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public void register(RegisterForm registerForm) {
        if(existsTheUsername(registerForm.getUsername()))
            throw new BadRequestException("????????????????????????????????????");
        UserEntity userEntity=registerForm.toUserEntity();
        userEntity.setPortrait(defaultPortraitUrl);
        userEntity.setPassword(passwordEncoder.encode(registerForm.getPassword()));
        userRepository.save(userEntity);

        Long userId=userEntity.getId();
        switch (registerForm.getUserType()){
            case STUDENT:
                studentService.addOneStudent(StudentEntity.builder().userId(userId).build());
                break;
            case TEACHER:
                teacherService.addOneTeacher(TeacherEntity.builder().userId(userId).build());
                break;
            default:
                throw new BadRequestException("????????????????????????");
        }
    }

    @Override
    public UserIdAndUserType getUserIdAndUserTypeByUserName(String username) {
        if(!existsTheUsername(username))
            throw new BadRequestException("????????????????????????"+username+"???????????????");

        UserIdAndUserType res=new UserIdAndUserType();

        Long userId=userRepository.findByUsername(username).getId();
        res.setUserId(userId);

        if(studentService.existsById(userId)){
            res.setUserType(UserType.STUDENT);
        } else if(teacherService.existsById(userId)){
            res.setUserType(UserType.TEACHER);
        } else {
            throw new RgmsException("id???"+userId+"??????????????????????????????????????????");
        }
        return res;
    }

    @Override
    public void editTrueName(Long userId, String trueName) {
        UserEntity userEntity=getUserEntityById(userId);
        userEntity.setTrueName(trueName);
        userRepository.save(userEntity);
    }

    @Override
    public void editEmail(Long userId, String email) {
        UserEntity userEntity=getUserEntityById(userId);
        userEntity.setEmail(email);
        userRepository.save(userEntity);
    }

    @Override
    public void exitResearchGroup(Long userId) {
        UserEntity userEntity=getUserEntityById(userId);
        if(userEntity.getGroupId()==null)
            throw new BadRequestException("???????????????????????????????????????");
        if(researchGroupService.getGroupCreator(userEntity.getGroupId()).equals(userId))
            throw new BadRequestException("???????????????????????????????????????????????????");
        Long lastGroupId=userEntity.getGroupId();
        userEntity.setGroupId(null);
        userRepository.save(userEntity);

        // ????????????????????????????????????????????????
        List<UserEntity> userEntities=userRepository.findByGroupId(lastGroupId);
        for(UserEntity entity : userEntities){
            Long id=entity.getId();
            messageService.sendMessage(userId, id, "??????{sender}??????????????????");
        }
    }

    @Override
    public void approveToJoinGroup(Long messageId) {
        MessageEntity messageEntity=messageService.getMessageEntityById(messageId);
        if(messageEntity.getType()!=MessageType.APPLY_TO_JOIN_GROUP)
            throw new BadRequestException("??????????????????????????????????????????????????????");
        Long groupCreator=messageEntity.getRecipient(), applier=messageEntity.getSender();
        UserEntity userEntity=getUserEntityById(applier);
        if(userEntity.getGroupId()!=null)
            throw new BadRequestException("??????????????????????????????????????????????????????");
        Long groupId=getUserEntityById(groupCreator).getGroupId();
        ResearchGroupEntity groupEntity=researchGroupService.getResearchGroupEntityById(groupId); // ????????????????????????id????????????
        userEntity.setGroupId(groupId);
        userRepository.save(userEntity);

        // ??????????????????????????????????????????????????????
        messageEntity.setType(MessageType.DEFAULT);
        messageEntity.setState(MessageState.READ);
        messageService.saveMessageEntity(messageEntity);

        // ????????????????????????????????????????????????
        List<UserEntity> userEntities=userRepository.findByGroupId(groupId);
        for(UserEntity entity : userEntities){
            Long id=entity.getId();
            if(!id.equals(applier)) {
                messageService.sendMessage(applier, id, "??????{sender}??????????????????");
            }
        }
        // ??????????????????????????????
        messageService.sendMessage(groupCreator, applier, groupEntity.getGroupName()+"??????????????????{sender}????????????????????????????????????");
    }

    public UserEntity getUserEntityById(Long userId){
        if(userId==null)
            throw new BadRequestException("???????????????id?????????null");
        Optional<UserEntity> maybeEntity=userRepository.findById(userId);
        if(!maybeEntity.isPresent())
            throw new NotFoundException("?????????id???"+userId+"????????????");
        return maybeEntity.get();
    }

    @Override
    public ResearchGroupEntity createAndJoinResearchGroup(GroupInfoWithoutId form) {
        if(!teacherService.existsById(form.getCreatorId()))
            throw new NotFoundException("???????????????id???"+form.getCreatorId()+"?????????");
        UserEntity userEntity=getUserEntityById(form.getCreatorId());
        if(userEntity.getGroupId()!=null)
            throw new BadRequestException("?????????????????????????????????");
        if(form.getDirections().size()==0)
            throw new BadRequestException("??????????????????????????????0");
        ResearchGroupEntity researchGroupEntity=researchGroupService.addOneResearchGroup(form.toResearchGroupEntity(directionSep));
        // ????????????????????????
        userEntity.setGroupId(researchGroupEntity.getId());
        userRepository.save(userEntity);
        return researchGroupEntity;
    }

    @Override
    public UserSimpleInfo getUserSimpleInfoById(Long userId) {
        UserEntity userEntity=getUserEntityById(userId);
        return UserSimpleInfo.builder()
                .userId(userId)
                .username(userEntity.getUsername())
                .trueName(userEntity.getTrueName())
                .portrait(userEntity.getPortrait())
                .build();
    }

    @Override
    public List<UserSimpleInfo> getAllMemberSimpleInfosInTheGroup(Long groupId) {
        List<UserEntity> userEntities=userRepository.findByGroupId(groupId);
        List<UserSimpleInfo> res=new ArrayList<>(userEntities.size());
        for(UserEntity userEntity : userEntities)
            res.add(userEntity.toUserSimpleInfo());
        return res;
    }

    @Override
    public boolean existsById(Long userId){
        return userRepository.existsById(userId);
    }

    @Override
    public void updateUserTrueNameAndEmailAndPortrait(UserTrueNameAndEmailAndPortrait info) {
        UserEntity userEntity=getUserEntityById(info.getUserId());
        userEntity.setTrueName(info.getTrueName());
        userEntity.setEmail(info.getEmail());
        userEntity.setPortrait(info.getPortrait());
        userRepository.save(userEntity);
    }

    @Override
    public void deleteResearchGroup(Long userId, Long groupId) {
        if(!researchGroupService.getGroupCreator(groupId).equals(userId))
            throw new BadRequestException("????????????????????????????????????????????????");
        userRepository.allMemberExitGroup(groupId);
        researchGroupService.deleteGroup(groupId);
    }

    @Override
    public void applyToJoinGroup(Long userId, Long groupId) {
        UserEntity userEntity=getUserEntityById(userId);
        if(userEntity.getGroupId()!=null)
            throw new BadRequestException("????????????????????????????????????????????????");
        if(!messageService.hasAppliedToJoinGroup(userId)) { // ?????????????????????
            messageService.sendMessage(userId, researchGroupService.getGroupCreator(groupId),
                    "??????{sender}???????????????????????????", MessageType.APPLY_TO_JOIN_GROUP, null);
        }
    }

    @Override
    public void refuseToJoinGroup(Long groupCreator, Long applier) {
        UserEntity userEntity=getUserEntityById(groupCreator);
        ResearchGroupEntity groupEntity=researchGroupService.getResearchGroupEntityById(userEntity.getGroupId());
        messageService.sendMessage(groupCreator, applier, groupEntity.getGroupName()+"??????????????????{sender}??????????????????????????????");
    }

    @Override
    public void applyToExitGroup(Long userId){
        UserEntity userEntity=getUserEntityById(userId);
        if(userEntity.getGroupId()==null)
            throw new BadRequestException("??????????????????????????????");
        if(!messageService.hasAppliedToExitGroup(userId)) { // ?????????????????????
            Long groupCreatorId = researchGroupService.getGroupCreator(userEntity.getGroupId());
            messageService.sendMessage(userId, groupCreatorId, "??????{sender}???????????????????????????",
                    MessageType.APPLY_TO_EXIT_GROUP, null);
        }
    }

    @Override
    public void approveToExitGroup(Long messageId){
        MessageEntity messageEntity=messageService.getMessageEntityById(messageId);
        if(messageEntity.getType()!=MessageType.APPLY_TO_EXIT_GROUP)
            throw new BadRequestException("??????????????????????????????????????????????????????");
        Long applier=messageEntity.getSender();
        UserEntity userEntity=getUserEntityById(applier);
        if(userEntity.getGroupId()==null)
            throw new BadRequestException("?????????????????????????????????");
        Long groupId=userEntity.getGroupId();
        userEntity.setGroupId(null);
        userRepository.save(userEntity);

        // ??????????????????????????????????????????????????????
        messageEntity.setType(MessageType.DEFAULT);
        messageEntity.setState(MessageState.READ);
        messageService.saveMessageEntity(messageEntity);

        // ????????????????????????????????????????????????
        List<UserEntity> userEntities=userRepository.findByGroupId(groupId);
        for(UserEntity entity : userEntities){
            Long id=entity.getId();
            if(!id.equals(applier)) {
                messageService.sendMessage(applier, id, "??????{sender}??????????????????");
            }
        }
        // ??????????????????????????????
        messageService.sendMessage(researchGroupService.getGroupCreator(groupId), applier,
                researchGroupService.getResearchGroupEntityById(groupId).getGroupName()+"??????????????????{sender}?????????????????????????????????");
    }

    @Override
    public void refuseToExitGroup(Long messageId){
        MessageEntity messageEntity=messageService.getMessageEntityById(messageId);
        if(messageEntity.getType()!=MessageType.APPLY_TO_EXIT_GROUP)
            throw new BadRequestException("??????????????? ????????????????????? ???????????????");
        UserEntity userEntity=getUserEntityById(messageEntity.getRecipient());
        ResearchGroupEntity groupEntity=researchGroupService.getResearchGroupEntityById(userEntity.getGroupId());
        messageService.sendMessage(messageEntity.getRecipient(), messageEntity.getSender(),
                groupEntity.getGroupName()+"??????????????????{sender}??????????????????????????????");
    }

    @Override
    public Long getGroupId(Long userId) {
        return getUserEntityById(userId).getGroupId();
    }
}
