// 切换到应用数据库
db = db.getSiblingDB('drawsee');

// 更新线性代数课程，关联知识库
db.course.updateOne(
    { 
        classCode: "123456"  // 线性代数课程的classCode
    },
    {
        $set: {
            knowledgeBaseIds: [
                ObjectId("在这里填入您的知识库ID"),  // 请替换为实际的知识库ID
                // 如果有多个知识库ID，可以继续添加，例如：
                // ObjectId("知识库ID2"),
                // ObjectId("知识库ID3")
            ],
            updatedAt: new Date()
        }
    }
);

// 打印更新结果
print("更新完成的课程信息：");
printjson(db.course.findOne({ classCode: "123456" }));