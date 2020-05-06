/**
 * Copyright 2016 SmartBear Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this collection except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.neverpile.fusion.rest.exception;

public class ApiException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  
  private final int code;

  public ApiException(final int code, final String msg) {
    super(msg);
    this.code = code;
  }

  public int getCode() {
    return code;
  }
}
