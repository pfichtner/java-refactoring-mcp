# inline-aggressive


### Command:
```
inline-aggressive --project {root} --dry-run
```

### Exit code: 0

### Output:
```
Dry run — no files written.
Would change (2):

=== Alpha.java ===
package com.example;

public class Alpha {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 3 * 2;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int twice(int value) {
        return value * 2;
    }
}

=== Beta.java ===
package com.example;

public class Beta {
    public int run() {
        int __inline_result;
		__inline_label: {
			{
				__inline_result = 5 + 10;
				break __inline_label;
			}
		}
		return __inline_result;
    }

    private int plusTen(int value) {
        return value + 10;
    }
}
```