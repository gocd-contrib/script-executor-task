GoCD task plugin to run Shell scripts.

*Usage:*

![alt tag](https://raw.githubusercontent.com/tw-leansw/script-executor-task/master/images/screenshot.png)

*Notes:*

This is only for convenience since you can run all commands directly through GoCD anyway.

This could be preferable because:
- Its exactly like shell script. GoCD's custom command takes a little getting used to.
- Its easier to CRUD commands since its a script, specially re-order them if required.

It should not be used as a replacement for important scripts like deployment scripts. They should remain in your repository.

*Usage:*

Download jar from releases & place it in `<go-server-location>/plugins/external` & restart GoCD Server.

## Contributing

We encourage you to contribute to GoCD. For information on contributing to this project, please see our [contributor's guide](http://www.go.cd/contribute).
A lot of useful information like links to user documentation, design documentation, mailing lists etc. can be found in the [resources](http://www.go.cd/community/resources.html) section.

## License

```plain
Copyright 2015 ThoughtWorks, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
