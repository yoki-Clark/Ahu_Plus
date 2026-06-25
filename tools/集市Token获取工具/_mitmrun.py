# -*- coding: utf-8 -*-
# mitmdump 启动垫片：`python -m mitmproxy.tools.main` 无效（该模块无 __main__ 守卫，
# -m 会静默退出）；本垫片显式调用 mitmdump() 入口，命令行参数经 sys.argv 自动传入。
from mitmproxy.tools.main import mitmdump
mitmdump()
