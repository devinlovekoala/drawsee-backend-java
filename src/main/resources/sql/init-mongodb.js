// MongoDB数据库初始化脚本

// 切换到应用数据库
db = db.getSiblingDB('drawsee');

// 删除已存在的课程集合
db.course.drop();

// 创建课程集合
db.createCollection("course");

// 创建系统默认课程
db.course.insertMany([
    {
        _id: ObjectId(),
        name: "线性代数",
        description: "帮助同学们直观可视化学习线性代数这门知识较为抽象的理工类重难点课程",
        code: "LA101",
        classCode: "123456",
        subject: "MATH",
        topics: ["线性代数", "矩阵", "向量"],
        creatorId: 2,
        creatorRole: "ADMIN",
        studentIds: [],
        knowledgeBaseIds: [ObjectId("67e8340675cba039dad48728")],
        createdAt: new Date(),
        updatedAt: new Date(),
        isDeleted: false
    },
    {
        _id: ObjectId(),
        name: "电子电路分析",
        description: "支持自搭电路图一键智能分析模式，帮助同学们更好地学习理解模电等理工类重难点课程",
        code: "EC101",
        classCode: "234567",
        subject: "ELECTRONIC",
        topics: ["电子", "电路分析"],
        creatorId: 2,
        creatorRole: "ADMIN",
        studentIds: [],
        knowledgeBaseIds: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        isDeleted: false
    },
    {
        _id: ObjectId(),
        name: "通用",
        description: "用户可以在其中自由询问广泛学科和知识面的问题，系统将自动选择有趣的模式回答",
        code: "GEN101",
        classCode: "345678",
        subject: "GENERAL",
        topics: ["通用", "基础学习"],
        creatorId: 1,
        creatorRole: "ADMIN",
        studentIds: [],
        knowledgeBaseIds: [],
        createdAt: new Date(),
        updatedAt: new Date(),
        isDeleted: false
    }
]);

// 创建索引
db.course.createIndex({ "classCode": 1 }, { unique: true });
db.course.createIndex({ "code": 1 }, { unique: true });

// 打印初始化完成信息
print("MongoDB课程集合初始化完成！");
print("已创建课程数：", db.course.count());