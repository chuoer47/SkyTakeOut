package com.sky.exception;

/**
 * 账号被锁定异常
 */
public class UserNotFoundException extends BaseException {

    public UserNotFoundException() {
    }

    public UserNotFoundException(String msg) {
        super(msg);
    }

}
