Face-to-Face (F2F) Auth
=======================

This app is based on the GithubBrowserSample architecture components example
provided by the Android team: https://github.com/googlesamples/android-architecture-components/tree/master/GithubBrowserSample

Much of the API plumbing present in the example has been removed or is largely
unused because of the more complex interaction necessary to interface with the
authentication server.

The largest sections of code written solely by me are the NFC-related message
construction and handling, the SearchFragment which contains the
authentication protocol logic, as well as the inter-fragment intent dispatch
(`LocalBroadcastManager`).

The `app/src/main/res/raw/local_cert2.pem` is the server's root TLS
certificate that is required to connect to the authentication server. I have
included a script to regenerate this certificate based on the current IP
address from which the authentication server is being served.

License
--------

Copyright 2017 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
