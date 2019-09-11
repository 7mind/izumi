
using System;
using System.Collections;
using System.Collections.Generic;

namespace IRT {
    public interface IServiceDispatcher<C, D> {
        D Dispatch(C ctx, string method, D data);
        string GetSupportedService();
        string[] GetSupportedMethods();
    }
}
