<template>
    <div class="register-container">
        <div class="register-box">
            <!--表单区域-->
            <el-form ref="registerFormRef" :model="registerForm" :rules="registerFormRules" label-width="0px" class="login-form">
                <el-form-item prop="username">
                    <el-input placeholder="请输入用户名" clearable
                              v-model="registerForm.username" prefix-icon="el-icon-user" ></el-input>
                </el-form-item>
                <el-form-item prop="password">
                    <el-input placeholder="请输入密码" show-password clearable
                              v-model="registerForm.password" prefix-icon="el-icon-lock" type="password" ></el-input>
                </el-form-item>
                <el-form-item prop="email">
                    <el-input placeholder="请输入电子邮箱" clearable
                              v-model="registerForm.email" prefix-icon="el-icon-message" ></el-input>
                </el-form-item>
                <el-form-item prop="userType">
                    <el-select v-model="registerForm.userType" placeholder="请选择用户类型" >
                        <el-option label="老师" value="TEACHER"></el-option>
                        <el-option label="学生" value="STUDENT"></el-option>
                    </el-select>
                </el-form-item>
                <el-form-item >
                    <el-input v-model="registerForm.check" prefix-icon="el-icon-edit-outline" clearable style="width:50%" placeholder="请输入验证码"></el-input><img  @click="changeCheckCode" :src="imgCode">

                </el-form-item>
                <!--按钮区域-->
                <el-form-item class="btns">
                    <el-button type="primary" round @click="register">注册</el-button>
                    <el-button round @click="backToLogin">返回</el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script>
    export default {
        data(){
            //验证邮箱的规则
            let checkEmail = (rule, value, callback)=>{
                //正则表达式
                const regEmail = /^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)/;
                if(regEmail.test(value)){
                    return callback();
                }
                callback(new Error("请输入合法的邮箱！"))
            }
            return{
                registerForm:{
                    username: '',
                    // trueName: '',
                    userType: '',
                    password: '',
                    email: '',
                    check:'',
                    sessionId:''
                },
                registerFormRules:{
                    username:[
                        { required: true, message: '请输入用户名称', trigger: 'blur' },
                        { min: 3, max: 10, message: '长度在 3 到 10 个字符', trigger: 'blur' }
                    ],
                    password:[
                        { required: true, message: '请输入用户密码', trigger: 'blur' },
                        { min: 5, max: 15, message: '长度在 5 到 15 个字符', trigger: 'blur' }
                    ],
                    email:[
                        { required: true, message: '请输入注册邮箱', trigger: 'blur' },
                        { validator: checkEmail, trigger:'blur'}
                    ],
                },
                imgCode:''
            }
        },
        created(){
            var that=this
            //this.$axios.post('/user/checkCode',{responseType: 'blob',withCredentials:true}).then(function(response){
                /*console.log(response.data)
                return 'data:image/png;base64,' /*+ btoa(
                    new Uint8Array(response.data).reduce((data, byte) => data + String.fromCharCode(byte), '')
                    )*/
                    //console.log(response.data)
                    //that.imgCode=window.URL.createObjectURL(new Blob([response.data]))
                //}).then(data => {
                    //that.imgCode = data
                    //console.log(that.imgCode)
                //})
                //console.log(that.imgCode)
                //var that=this
            this.$axios.post('/session/getSession').then(
                function(response){
                    that.registerForm.sessionId=response.data
                    console.log(that.registerForm.sessionId)


                    that.$axios.post('/user/checkCode?sessionId='+that.registerForm.sessionId,{},{/*withCredentials:true,*/responseType: 'arraybuffer'}).then(function(response){
                    console.log(response)
                        return 'data:image/png;base64,' + btoa(
                        new Uint8Array(response.data)
                        .reduce((data, byte) => data + String.fromCharCode(byte), '')
                        )
                    }).then(data => {
                        that.imgCode = data
                    })
                }

            )
        },
        methods:{
            backToLogin(){
                this.$router.push('/login');
            },
            register(){
                this.$refs.registerFormRef.validate(async valid => {
                    if(!valid){
                        this.$message.error({message: '注册信息校验未通过，请重新注册！', duration:1500, showClose:true});
                        this.changeCheckCode()
                    }else{
                        let form = {};
                        form.sessionId=this.registerForm.sessionId;
                        form.check=this.registerForm.check;
                        form.username = this.registerForm.username;
                        form.password = this.registerForm.password;
                        form.email = this.registerForm.email;
                        form.userType = this.registerForm.userType;
                        this.$axios.post('/user/register',form).then(res => {
                            if(res.data !== ""){
                                this.$message.error({message: res.data.errorMsg,duration:1500, showClose:true});
                                this.changeCheckCode()
                            }else{
                                this.$router.push('/login');
                                this.$message.success({message: "注册成功！请前往注册邮箱激活该账号",duration:1500, showClose:true})
                            }
                        }).catch(err => {
                            this.$message.error({message: err, duration: 1500, showClose: true});
                        })
                    }
                })
            },
            changeCheckCode(){
                var that=this
                /*this.$axios.post('/user/checkCode',{responseType: 'arraybuffer'},{withCredentials:true}).then(function(response){
                    console.log(response)
                return 'data:image/png;base64,' + btoa(
                    new Uint8Array(response.data)
                    .reduce((data, byte) => data + String.fromCharCode(byte), '')
                    )
                }).then(data => {
                    that.imgCode = data
                })*/
                this.$axios.post('/user/checkCode?sessionId='+that.registerForm.sessionId,{},{/*withCredentials:true,*/responseType: 'arraybuffer'}).then(function(response){
                    console.log(response)
                return 'data:image/png;base64,' + btoa(
                    new Uint8Array(response.data)
                    .reduce((data, byte) => data + String.fromCharCode(byte), '')
                    )
                }).then(data => {
                    that.imgCode = data
                })
                //console.log(that.imgCode)
            }
        }
    }
</script>

<style scoped lang="less">
.register-container{
    background-color: #2b4b6b;
    height: 100%;
}
.register-box{
    width: 450px;
    height: 400px;
    background-color: white;
    border-radius: 5px;
    position: absolute;
    left: 50%;
    top: 50%;
    /*横轴、纵轴各移动50%*/
    transform:translate(-50%,-50%);

    .btns{
        display: flex;
        justify-content: flex-end;
    }
    .login-form{
        position: absolute;
        bottom: 0;
        width: 100%;
        padding: 0 20px;
        -webkit-box-sizing: border-box;
        -moz-box-sizing: border-box;
        box-sizing: border-box;

    }
}
</style>